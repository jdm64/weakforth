import java.util.*

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val vm = VM()
        val input = TxtInput()
        val runner = Interpreter(vm, input)
        runner.run()
    }
}

internal class Function(name: String) {
    var name = ""
    var code: MutableList<Int> = ArrayList()
    var callback: (()->Unit)? = null
    var immediate = false

    fun pushInt(`val`: Int) {
        code.add(`val`)
    }

    fun pushOpcode(opcode: Int, `val`: Int) {
        code.add(opcode)
        code.add(`val`)
    }

    fun run() {
        callback?.let { it() }
    }

    init {
        this.name = name
    }
}

internal class FunctionTable {
    var items: MutableList<Function> = ArrayList()

    operator fun get(id: Int): Function {
        return items[id]
    }

    fun find(name: String, id: IntArray): Function? {
        for (i in items.indices) {
            val item = items[i]
            if (name == item.name) {
                id[0] = i
                return item
            }
        }
        return null
    }

    fun add(name: String): Int {
        items.add(Function(name))
        return items.size - 1
    }

    fun setCallback(id: Int, call: ()->Unit) {
        items[id].callback = call
    }
}

internal enum class OpCode {
    Call, Jump, Prompt, PushNum, Read, Return, ERROR;

    companion object {
        val cache = values()

        fun valueOf(opCode: Int): OpCode {
            return if (opCode < 0 || opCode >= cache.size) {
                ERROR
            } else cache[opCode]
        }
    }
}

internal enum class Mode {
    Execute, Compile
}

internal class ReturnData(var id: Int, var pc: Int)

internal class VM {
    var ftable = FunctionTable()
    var dstack: Deque<Int> = ArrayDeque()
    var rstack: Deque<ReturnData> = ArrayDeque()
    var fc = 0
    var pc = -1
    var mode = Mode.Execute
    var runVM = true

    fun getOpcode(idx: Int): Int {
        return ftable[fc].code[idx]
    }

    fun currOpcode(): Int {
        return getOpcode(pc)
    }

    fun nextOpcode(): Int {
        pc += 1
        return getOpcode(pc)
    }

    fun nextInt(): Int {
        return nextOpcode()
    }

    fun findFunc(name: String, id: IntArray): Function? {
        return ftable.find(name, id)
    }

    fun addFunc(name: String): Int {
        return ftable.add(name)
    }

    fun callFunc(id: Int) {
        val ptr = ftable[id]
        if (!ptr.code.isEmpty()) {
            rstack.addLast(ReturnData(fc, pc))
            fc = id
            pc = -1
        } else {
            ptr.run()
        }
    }

    fun call() {
        callFunc(nextInt())
    }

    fun jump() {
        val n = nextInt() - 1
        pc += n
    }

    fun pushNum() {
        dstack.add(nextInt())
    }

    fun returnFunc() {
        val data = rstack.removeLast()
        fc = data.id
        pc = data.pc
    }

    fun exit() {
        runVM = false
    }

    fun stackAdd() {
        val a = dstack.removeLast()
        val b = dstack.removeLast()
        dstack.addLast(b + a)
    }

    fun stackSub() {
        val a = dstack.removeLast()
        val b = dstack.removeLast()
        dstack.addLast(b - a)
    }

    fun stackMul() {
        val a = dstack.removeLast()
        val b = dstack.removeLast()
        dstack.addLast(b * a)
    }

    fun stackDiv() {
        val a = dstack.removeLast()
        val b = dstack.removeLast()
        dstack.addLast(b / a)
    }

    fun stackDup() {
        dstack.addLast(dstack.last)
    }

    fun stackPop() {
        dstack.removeLast()
    }

    fun stackClr() {
        dstack.clear()
    }

    fun stackSwp() {
        val a = dstack.removeLast()
        val b = dstack.removeLast()
        dstack.addLast(a)
        dstack.addLast(b)
    }

    fun printTopStack() {
        if (!dstack.isEmpty()) {
            println(dstack.last)
        } else {
            println("<empty>")
        }
    }

    fun printStack() {
        print("[ ")
        for (item in dstack) {
            print("$item ")
        }
        println("]")
    }

    fun addCallback(name: String, ptr: ()->Unit) {
        val id = addFunc(name)
        ftable.setCallback(id, ptr)
    }

    fun setupFunctions() {
        addCallback(".", { printTopStack() })
        addCallback("..", { printStack() })
        addCallback("+", { stackAdd() })
        addCallback("-", { stackSub() })
        addCallback("*", { stackMul() })
        addCallback("/", { stackDiv() })
        addCallback("dup", { stackDup() })
        addCallback("pop", { stackPop() })
        addCallback("clr", { stackClr() })
        addCallback("swp", { stackSwp() })
        addCallback("exit", { exit() })
    }

    init {
        setupFunctions()
    }
}

internal class TxtInput {
    var buff: Queue<String> = ArrayDeque()

    fun empty(): Boolean {
        return buff.isEmpty()
    }

    fun printError(msg: String?) {
        println(msg)
        buff.clear()
        buff.add("")
    }

    fun readLine() {
        val words = Scanner(System.`in`).nextLine().split(" ".toRegex()).toTypedArray()
        if (words.size > 0) {
            buff.addAll(Arrays.asList(*words))
        } else {
            buff.add("")
        }
    }

    fun nextWord(): String {
        if (buff.isEmpty()) {
            readLine()
            buff.add("")
        }
        return buff.remove()
    }
}

internal class Interpreter(var vm: VM, var input: TxtInput) {
    var defFunc: Function? = null

    fun execWord(word: String): Boolean {
        val isExec = vm.mode == Mode.Execute
        val id = intArrayOf(0)
        val ptr = vm.findFunc(word, id)

        if (ptr != null) {
            if (ptr.immediate) {
                ptr.run()
            } else if (isExec) {
                vm.callFunc(id[0])
                return ptr.code.isEmpty()
            } else {
                defFunc!!.pushOpcode(OpCode.Call.ordinal, id[0])
            }
        } else {
            try {
                val num = Integer.valueOf(word)
                if (isExec) {
                    vm.dstack.add(num)
                } else {
                    defFunc!!.pushOpcode(OpCode.PushNum.ordinal, num)
                }
            } catch (e: NumberFormatException) {
                input.printError("Error: `$word` not a function or a number")
            }
        }
        return true
    }

    fun read(): Boolean {
        val word = input.nextWord()
        return if (word.isEmpty()) {
            false
        } else execWord(word)
    }

    fun prompt() {
        if (input.empty()) {
            print(if (vm.mode == Mode.Execute) "\n> " else "...> ")
        }

        while (read()) {
        }
    }

    fun endDefineFunc() {
        vm.mode = Mode.Execute
        if (defFunc != null) {
            defFunc!!.pushInt(OpCode.Return.ordinal)
            defFunc = null
        }
    }

    fun defineFunc() {
        vm.mode = Mode.Compile
        var name = input.nextWord()
        while (name.isEmpty()) {
            prompt()
            name = input.nextWord()
        }

        val id = intArrayOf(0)
        val ptr = vm.findFunc(name, id)
        if (ptr != null) {
            endDefineFunc()
            input.printError("Function already defined: $name")
            return
        }
        defFunc = vm.ftable[vm.addFunc(name)]
    }

    fun run() {
        while (vm.runVM) {
            when (OpCode.valueOf(vm.nextOpcode())) {
                OpCode.Call -> vm.call()
                OpCode.Jump -> vm.jump()
                OpCode.Prompt -> prompt()
                OpCode.PushNum -> vm.pushNum()
                OpCode.Read -> read()
                OpCode.Return -> vm.returnFunc()
                else -> {
                    val curr = vm.currOpcode()
                    println("Invalid Opcode: " + curr + "; At Instruction: " + vm.pc)
                }
            }
        }
    }

    init {
        var id = vm.addFunc(" ")
        var ptr = vm.ftable[id]
        ptr.pushInt(OpCode.Prompt.ordinal)
        ptr.pushOpcode(OpCode.Jump.ordinal, -2)
        vm.fc = id

        id = vm.addFunc(":")
        ptr = vm.ftable[id]
        ptr.callback = { defineFunc() }

        id = vm.addFunc(";")
        ptr = vm.ftable[id]
        ptr.callback = { endDefineFunc() }
        ptr.immediate = true
    }
}
