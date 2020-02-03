
class Function
{
	var name: String = ""
	var code: [Int8] = []
	var callback: ()->Void = {}
	var immediate: Bool = false

	init(_ Name: String)
	{
		name = Name
	}

	func pushInt(_ val: Int8)
	{
		code.append(val)
	}

	func pushOpcode(_ opcode: Int8, _ val: Int8)
	{
		code += [opcode, val]
	}

	func run()
	{
		callback()
	}
}

class FunctionTable
{
	var items: [Function] = []

	func get(_ id: Int) -> Function
	{
		return items[id]
	}

	func find(_ name: String, _ id: inout Int) -> Function?
	{
		for i in 0 ..< items.count {
			if name == items[i].name {
				id = i
				return items[i]
			}
		}
		return nil
	}


	func add(_ name: String) -> Int
	{
		items.append(Function(name))
		return items.count - 1
	}

	func setCallback(_ id: Int, _ call: @escaping ()->Void)
	{
		items[id].callback = call
	}
}

enum OpCode: Int8
{
	case Call, Jump, Prompt, PushNum, Read, Return
}

enum Mode
{
	case Execute, Compile
}

struct ReturnData
{
	var id: Int
	var pc: Int
}

class VM
{
	var ftable: FunctionTable = FunctionTable()
	var dstack: [Int] = []
	var rstack: [ReturnData] = []
	var fc: Int = 0
	var pc: Int = -1
	var mode: Mode = Mode.Execute
	var runVM: Bool = true

	init()
	{
		setupFunctions()
	}

	func getOpcode(_ idx: Int) -> Int8
	{
		return ftable.get(fc).code[idx]
	}

	func currOpcode() -> Int8
	{
		return getOpcode(pc)
	}

	func nextOpcode() -> Int8
	{
		pc += 1
		return getOpcode(pc)
	}

	func nextInt() -> Int
	{
		return Int(nextOpcode())
	}

	func findFunc(_ name: String, _ id: inout Int) -> Function?
	{
		return ftable.find(name, &id)
	}

	func addFunc(_ name: String) -> Int
	{
		return ftable.add(name)
	}

	func callFunc(_ id: Int)
	{
		let ptr = ftable.get(id)
		if !ptr.code.isEmpty {
			rstack.append(ReturnData(id: fc, pc: pc))
			fc = id
			pc = -1
		} else {
			ptr.run()
		}
	}

	func call()
	{
		callFunc(nextInt())
	}

	func jump()
	{
		pc += nextInt() - 1
	}

	func pushNum()
	{
		dstack.append(nextInt())
	}

	func returnFunc()
	{
		let data = rstack.removeLast()
		fc = data.id
		pc = data.pc
	}

	func exit()
	{
		runVM = false
	}

	func stackAdd()
	{
		let a = dstack.removeLast()
		let b = dstack.removeLast()
		dstack.append(b + a)
	}

	func stackSub()
	{
		let a = dstack.removeLast()
		let b = dstack.removeLast()
		dstack.append(b - a)
	}

	func stackMul()
	{
		let a = dstack.removeLast()
		let b = dstack.removeLast()
		dstack.append(b * a)
	}

	func stackDiv()
	{
		let a = dstack.removeLast()
		let b = dstack.removeLast()
		dstack.append(b / a)
	}

	func stackDup()
	{
		dstack.append(dstack.last!)
	}

	func stackPop()
	{
		dstack.removeLast()
	}

	func stackClr()
	{
		dstack.removeAll()
	}

	func stackSwp()
	{
		let a = dstack.removeLast()
		let b = dstack.removeLast()
		dstack.append(a)
		dstack.append(b)
	}

	func printTopStack()
	{
		if !dstack.isEmpty {
			print(dstack.last!)
		} else {
			print("<empty>")
		}
	}

	func printStack()
	{
		print("[ ", terminator: "")
		for item in dstack {
			print("\(item) ", terminator: "")
		}
		print("]")
	}

	func addCallback(_ name: String, _ ptr: @escaping ()->Void)
	{
		let id = addFunc(name)
		ftable.setCallback(id, ptr)
	}

	func setupFunctions()
	{
		addCallback(".", { self.printTopStack() })
		addCallback("..", { self.printStack() })

		addCallback("+", { self.stackAdd() })
		addCallback("-", { self.stackSub() })
		addCallback("*", { self.stackMul() })
		addCallback("/", { self.stackDiv() })

		addCallback("dup", { self.stackDup() })
		addCallback("pop", { self.stackPop() })
		addCallback("clr", { self.stackClr() })
		addCallback("swp", { self.stackSwp() })
		addCallback("exit", { self.exit() })
	}
}

class TxtInput
{
	var buff: [String] = []

	func empty() -> Bool
	{
		return buff.isEmpty
	}

	func printError(_ msg: String)
	{
		print(msg)
		buff.removeAll()
		buff.append("")
	}

	func loadLine()
	{
		let rawLine = readLine()
		if let line = rawLine {
			let words = line.split(separator: " ")
			for word in words {
				buff.append(String(word))
			}
		} else {
			buff.append("")
		}
	}

	func nextWord() -> String
	{
		if buff.isEmpty {
			loadLine()
			buff.append("")
		}
		return buff.removeFirst()
	}
}

class Interpreter
{
	var vm: VM
	var defId: Int
	var input: TxtInput

	func getDefFunc() -> Function
	{
		return vm.ftable.get(defId)
	}

	func execWord(_ word: String) -> Bool
	{
		let isExec = vm.mode == Mode.Execute
		var id: Int = 0
		let ptr = vm.findFunc(word, &id)

		if let fn = ptr {
			if fn.immediate {
				fn.run()
			} else if isExec {
				vm.callFunc(id)
				return fn.code.isEmpty
			} else {
				getDefFunc().pushOpcode(Int8(OpCode.Call.rawValue), Int8(id))
			}
		} else {
			let rawNum = Int(word)
			if let num = rawNum {
				if isExec {
					vm.dstack.append(num)
				} else {
					getDefFunc().pushOpcode(OpCode.PushNum.rawValue, Int8(num))
				}
			} else {
				input.printError("Error: `\(word)` not a function or a number")
			}
		}
		return true
	}

	func read() -> Bool
	{
		let word = input.nextWord()
		if word.isEmpty {
			return false
		}
		return execWord(word)
	}

	func prompt()
	{
		if input.empty() {
			print(vm.mode == Mode.Execute ? "\n> " : "...> ", terminator: "")
		}

		while read() {}
	}

	func endDefineFunc()
	{
		vm.mode = Mode.Execute
		if defId >= 0 {
			getDefFunc().pushInt(OpCode.Return.rawValue)
			defId = -1
		}
	}

	func defineFunc()
	{
		vm.mode = Mode.Compile
		var name = input.nextWord()
		while name.isEmpty {
			prompt()
			name = input.nextWord()
		}

		var id: Int = 0
		let ptr = vm.findFunc(name, &id)
		if ptr != nil {
			endDefineFunc()
			input.printError("Function already defined: \(name)")
			return
		}

		defId = vm.addFunc(name)
	}

	func run()
	{
		while vm.runVM {
			switch OpCode(rawValue: vm.nextOpcode()) {
			case .Call:
				vm.call()
			case .Jump:
				vm.jump()
			case .Prompt:
				prompt()
			case .PushNum:
				vm.pushNum()
			case .Read:
				let _ = read()
			case .Return:
				vm.returnFunc()
			default:
				let curr = vm.currOpcode()
				let at = vm.pc
				print("Invalid Opcode: \(curr); At Instruction: \(at)")
			}
		}
	}

	init(_ vm: VM, _ input: TxtInput)
	{
		self.vm = vm
		self.input = input
		defId = -1

		var id = vm.addFunc(" ")
		var ptr = vm.ftable.get(id)
		ptr.pushInt(OpCode.Prompt.rawValue)
		ptr.pushOpcode(OpCode.Jump.rawValue, -2)
		vm.fc = id

		id = vm.addFunc(":")
		ptr = vm.ftable.get(id)
		ptr.callback = { self.defineFunc() }

		id = vm.addFunc(";")
		ptr = vm.ftable.get(id)
		ptr.callback = { self.endDefineFunc() }
		ptr.immediate = true
	}
}

let vm = VM()
let input = TxtInput()
let runner = Interpreter(vm, input)

runner.run()
