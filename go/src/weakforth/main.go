package main

import (
	"bufio"
	"fmt"
	"os"
	"strconv"
	"strings"
)

func popLastInt(arr []int) (int, []int) {
	return arr[len(arr)-1], arr[:len(arr)-1]
}

func popLastRData(arr []ReturnData) (ReturnData, []ReturnData) {
	return arr[len(arr)-1], arr[:len(arr)-1]
}

type Function struct {
	name      string
	code      []int
	callback  func()
	immediate bool
}

func (this *Function) pushInt(val int) {
	this.code = append(this.code, val)
}

func (this *Function) pushOpcode(opcode int, val int) {
	this.code = append(this.code, opcode)
	this.code = append(this.code, val)
}

func (this *Function) run() {
	this.callback()
}

func NewFunction(name string) *Function {
	this := new(Function)
	this.name = name
	this.code = []int{}
	return this
}

type FunctionTable struct {
	items []Function
}

func (this *FunctionTable) get(id int) *Function {
	return &this.items[id]
}

func (this *FunctionTable) find(name string, id *int) *Function {
	for i := 0; i < len(this.items); i++ {
		item := this.items[i]
		if name == item.name {
			*id = i
			return &item
		}
	}
	return nil
}

func (this *FunctionTable) add(name string) int {
	this.items = append(this.items, *NewFunction(name))
	return len(this.items) - 1
}

func (this *FunctionTable) setCallback(id int, call func()) {
	this.items[id].callback = call
}

const (
	Call = iota
	Jump
	Prompt
	PushNum
	Read
	Return
	ERROR
)

const (
	Execute = iota
	Compile
)

type ReturnData struct {
	id int
	pc int
}

type VM struct {
	ftable FunctionTable
	dstack []int
	rstack []ReturnData
	fc     int
	pc     int
	mode   int
	runVM  bool
}

func (this *VM) getOpcode(idx int) int {
	return this.ftable.items[this.fc].code[idx]
}

func (this *VM) currOpcode() int {
	return this.getOpcode(this.pc)
}

func (this *VM) nextOpcode() int {
	this.pc += 1
	return this.getOpcode(this.pc)
}

func (this *VM) nextInt() int {
	return this.nextOpcode()
}

func (this *VM) findFunc(name string, id *int) *Function {
	return this.ftable.find(name, id)
}

func (this *VM) addFunc(name string) int {
	return this.ftable.add(name)
}

func (this *VM) callFunc(id int) {
	ptr := this.ftable.items[id]
	if len(ptr.code) > 0 {
		this.rstack = append(this.rstack, ReturnData{this.fc, this.pc})
		this.fc = id
		this.pc = -1
	} else {
		ptr.run()
	}
}

func (this *VM) call() {
	this.callFunc(this.nextInt())
}

func (this *VM) jump() {
	this.pc += this.nextInt() - 1
}

func (this *VM) pushNum() {
	this.dstack = append(this.dstack, this.nextInt())
}

func (this *VM) returnFunc() {
	var last ReturnData
	last, this.rstack = popLastRData(this.rstack)
	this.fc, this.pc = last.id, last.pc
}

func (this *VM) exit() {
	this.runVM = false
}

func (this *VM) stackAdd() {
	var a, b int
	a, this.dstack = popLastInt(this.dstack)
	b, this.dstack = popLastInt(this.dstack)
	this.dstack = append(this.dstack, b+a)
}

func (this *VM) stackSub() {
	var a, b int
	a, this.dstack = popLastInt(this.dstack)
	b, this.dstack = popLastInt(this.dstack)
	this.dstack = append(this.dstack, b-a)
}

func (this *VM) stackMul() {
	var a, b int
	a, this.dstack = popLastInt(this.dstack)
	b, this.dstack = popLastInt(this.dstack)
	this.dstack = append(this.dstack, b*a)
}

func (this *VM) stackDiv() {
	var a, b int
	a, this.dstack = popLastInt(this.dstack)
	b, this.dstack = popLastInt(this.dstack)
	this.dstack = append(this.dstack, b/a)
}

func (this *VM) stackDup() {
	this.dstack = append(this.dstack, this.dstack[len(this.dstack)-1])
}

func (this *VM) stackPop() {
	_, this.dstack = popLastInt(this.dstack)
}

func (this *VM) stackClr() {
	this.dstack = []int{}
}

func (this *VM) stackSwp() {
	var a, b int
	a, this.dstack = popLastInt(this.dstack)
	b, this.dstack = popLastInt(this.dstack)
	this.dstack = append(this.dstack, a)
	this.dstack = append(this.dstack, b)
}

func (this *VM) printTopStack() {
	if len(this.dstack) > 0 {
		fmt.Println(this.dstack[len(this.dstack)-1])
	} else {
		fmt.Println("<empty>")
	}
}

func (this *VM) printStack() {
	fmt.Print("[ ")
	for i := 0; i < len(this.dstack); i++ {
		fmt.Print(this.dstack[i], " ")
	}
	fmt.Println("]")
}

func (this *VM) addCallback(name string, call func()) {
	id := this.addFunc(name)
	this.ftable.setCallback(id, call)
}

func (this *VM) setupFunctions() {
	this.addCallback(".", this.printTopStack)
	this.addCallback("..", this.printStack)
	this.addCallback("+", this.stackAdd)
	this.addCallback("-", this.stackSub)
	this.addCallback("*", this.stackMul)
	this.addCallback("/", this.stackDiv)
	this.addCallback("dup", this.stackDup)
	this.addCallback("pop", this.stackPop)
	this.addCallback("clr", this.stackClr)
	this.addCallback("swp", this.stackSwp)
	this.addCallback("exit", this.exit)
}

func NewVM() *VM {
	this := new(VM)
	this.ftable = FunctionTable{[]Function{}}
	this.dstack = []int{}
	this.rstack = []ReturnData{}
	this.mode = Execute
	this.runVM = true
	this.setupFunctions()
	return this
}

type TxtInput struct {
	buff []string
}

func NewTxtInput() *TxtInput {
	this := new(TxtInput)
	this.buff = []string{}
	return this
}

func (this *TxtInput) empty() bool {
	return len(this.buff) == 0
}

func (this *TxtInput) printError(msg string) {
	fmt.Println(msg)
	this.buff = []string{}
	this.buff = append(this.buff, "")
}

func (this *TxtInput) readLine() {
	scanner := bufio.NewScanner(os.Stdin)
	scanner.Scan()
	line := scanner.Text()

	words := strings.Fields(line)
	if len(words) > 0 {
		this.buff = append(this.buff, words...)
	} else {
		this.buff = append(this.buff, "")
	}
}

func (this *TxtInput) nextWord() string {
	if len(this.buff) == 0 {
		this.readLine()
		this.buff = append(this.buff, "")
	}
	var ret string
	ret, this.buff = this.buff[0], this.buff[1:]
	return ret
}

type Interpreter struct {
	vm      *VM
	input   *TxtInput
	defFunc *Function
}

func (this *Interpreter) execWord(word string) bool {
	isExec := this.vm.mode == Execute
	var id = 0
	ptr := this.vm.findFunc(word, &id)

	if ptr != nil {
		if ptr.immediate {
			ptr.run()
		} else if isExec {
			this.vm.callFunc(id)
			return len(ptr.code) == 0
		} else {
			this.defFunc.pushOpcode(Call, id)
		}
	} else {
		num, err := strconv.Atoi(word)
		if err != nil {
			this.input.printError("Error: `$word` not a function or a number")
		} else if isExec {
			this.vm.dstack = append(this.vm.dstack, num)
		} else {
			this.defFunc.pushOpcode(PushNum, num)
		}
	}
	return true
}

func (this *Interpreter) read() bool {
	word := this.input.nextWord()
	if len(word) == 0 {
		return false
	}
	return this.execWord(word)
}

func (this *Interpreter) prompt() {
	if this.input.empty() {
		var msg string
		if this.vm.mode == Execute {
			msg = "\n> "
		} else {
			msg = "...> "
		}
		fmt.Print(msg)
	}

	for this.read() {
	}
}

func (this *Interpreter) endDefineFunc() {
	this.vm.mode = Execute
	if this.defFunc != nil {
		this.defFunc.pushInt(Return)
		this.defFunc = nil
	}
}

func (this *Interpreter) defineFunc() {
	this.vm.mode = Compile
	name := this.input.nextWord()
	for len(name) == 0 {
		this.prompt()
		name = this.input.nextWord()
	}

	id := 0
	ptr := this.vm.findFunc(name, &id)
	if ptr != nil {
		this.endDefineFunc()
		this.input.printError("Function already defined: $name")
		return
	}
	this.defFunc = this.vm.ftable.get(this.vm.addFunc(name))
}

func (this *Interpreter) run() {
	for this.vm.runVM {
		switch this.vm.nextOpcode() {
		case Call:
			this.vm.call()
		case Jump:
			this.vm.jump()
		case Prompt:
			this.prompt()
		case PushNum:
			this.vm.pushNum()
		case Read:
			this.read()
		case Return:
			this.vm.returnFunc()
		default:
			curr := this.vm.currOpcode()
			fmt.Println("Invalid Opcode: ", curr, "; At Instruction: ", this.vm.pc)
		}
	}
}

func NewInterpreter(vm *VM, input *TxtInput) *Interpreter {
	this := new(Interpreter)
	this.vm = vm
	this.input = input

	id := this.vm.addFunc(" ")
	ptr := this.vm.ftable.get(id)
	ptr.pushInt(Prompt)
	ptr.pushOpcode(Jump, -2)
	this.vm.fc = id

	id = this.vm.addFunc(":")
	ptr = this.vm.ftable.get(id)
	ptr.callback = this.defineFunc

	id = this.vm.addFunc(";")
	ptr = this.vm.ftable.get(id)
	ptr.callback = this.endDefineFunc
	ptr.immediate = true

	return this
}

func main() {
	vm := NewVM()
	input := NewTxtInput()
	runner := NewInterpreter(vm, input)
	runner.run()
}
