
import java.util.*;

public class Main
{
	public static void main(String[] args)
	{
		var vm = new VM();
		var input = new TxtInput();
		var runner = new Interpreter(vm, input);

		runner.run();
	}
}

interface Callback
{
	void call();
}

class Function
{
	String name = "";
	List<Integer> code = new ArrayList<>();
	Callback callback;
	boolean immediate = false;

	Function(String name)
	{
		this.name = name;
	}

	void pushInt(int val)
	{
		code.add(val);
	}

	void pushOpcode(int opcode, int val)
	{
		code.add(opcode);
		code.add(val);
	}

	void run()
	{
		callback.call();
	}
}

class FunctionTable
{
	List<Function> items = new ArrayList<>();

	Function get(int id)
	{
		return items.get(id);
	}

	Function find(String name, int[] id)
	{
		for (int i = 0; i < items.size(); i++) {
			var item = items.get(i);
			if (name.equals(item.name)) {
				id[0] = i;
				return item;
			}
		}
		return null;
	}

	int add(String name)
	{
		items.add(new Function(name));
		return items.size() - 1;
	}

	void setCallback(int id, Callback call)
	{
		items.get(id).callback = call;
	}
}

enum OpCode
{
	Call, Jump, Prompt, PushNum, Read, Return, ERROR;

	static final OpCode[] cache = values();

	public static OpCode valueOf(int opCode)
	{
		if (opCode < 0 || opCode >= cache.length) {
			return ERROR;
		}
		return cache[opCode];
	}
}

enum Mode
{
	Execute, Compile
}

class ReturnData
{
	int id;
	int pc;

	ReturnData(int ID, int PC)
	{
		id = ID;
		pc = PC;
	}
}

class VM
{
	FunctionTable ftable = new FunctionTable();
	Deque<Integer> dstack = new ArrayDeque<>();
	Deque<ReturnData> rstack = new ArrayDeque<>();
	int fc = 0;
	int pc = -1;
	Mode mode = Mode.Execute;
	boolean runVM = true;

	VM()
	{
		setupFunctions();
	}

	int getOpcode(int idx)
	{
		return ftable.get(fc).code.get(idx);
	}

	int currOpcode()
	{
		return getOpcode(pc);
	}

	int nextOpcode()
	{
		pc += 1;
		return getOpcode(pc);
	}

	int nextInt()
	{
		return nextOpcode();
	}

	Function findFunc(String name, int[] id)
	{
		return ftable.find(name, id);
	}

	int addFunc(String name)
	{
		return ftable.add(name);
	}

	void callFunc(int id)
	{
		var ptr = ftable.get(id);
		if (!ptr.code.isEmpty()) {
			rstack.addLast(new ReturnData(fc, pc));
			fc = id;
			pc = -1;
		} else {
			ptr.run();
		}
	}

	void call()
	{
		callFunc(nextInt());
	}

	void jump()
	{
		pc += nextInt() - 1;
	}

	void pushNum()
	{
		dstack.add(nextInt());
	}

	void returnFunc()
	{
		var data = rstack.removeLast();
		fc = data.id;
		pc = data.pc;
	}

	void exit()
	{
		runVM = false;
	}

	void stackAdd()
	{
		var a = dstack.removeLast();
		var b = dstack.removeLast();
		dstack.addLast(b + a);
	}

	void stackSub()
	{
		var a = dstack.removeLast();
		var b = dstack.removeLast();
		dstack.addLast(b - a);
	}

	void stackMul()
	{
		var a = dstack.removeLast();
		var b = dstack.removeLast();
		dstack.addLast(b * a);
	}

	void stackDiv()
	{
		var a = dstack.removeLast();
		var b = dstack.removeLast();
		dstack.addLast(b / a);
	}

	void stackDup()
	{
		dstack.addLast(dstack.getLast());
	}

	void stackPop()
	{
		dstack.removeLast();
	}

	void stackClr()
	{
		dstack.clear();
	}

	void stackSwp()
	{
		var a = dstack.removeLast();
		var b = dstack.removeLast();
		dstack.addLast(a);
		dstack.addLast(b);
	}

	void printTopStack()
	{
		if (!dstack.isEmpty()) {
			System.out.println(dstack.getLast());
		} else {
			System.out.println("<empty>");
		}
	}

	void printStack()
	{
		System.out.print("[ ");
		for (int item : dstack) {
			System.out.print(item + " ");
		}
		System.out.println("]");
	}

	void addCallback(String name, Callback ptr)
	{
		var id = addFunc(name);
		ftable.setCallback(id, ptr);
	}

	void setupFunctions()
	{
		addCallback(".", () -> printTopStack()) ;
		addCallback("..", () -> printStack());

		addCallback("+", () -> stackAdd());
		addCallback("-", () -> stackSub());
		addCallback("*", () -> stackMul());
		addCallback("/", () -> stackDiv());

		addCallback("dup", () -> stackDup());
		addCallback("pop", () -> stackPop());
		addCallback("clr", () -> stackClr());
		addCallback("swp", () -> stackSwp());
		addCallback("exit", () -> exit());
	}
}

class TxtInput
{
	Queue<String> buff = new ArrayDeque<>();

	boolean empty()
	{
		return buff.isEmpty();
	}

	void printError(String msg)
	{
		System.out.println(msg);
		buff.clear();
		buff.add("");
	}

	@SuppressWarnings("resource")
	void readLine()
	{
		var words = new Scanner(System.in).nextLine().split(" ");
		if (words.length > 0) {
			buff.addAll(Arrays.asList(words));
		} else {
			buff.add("");
		}
	}

	String nextWord()
	{
		if (buff.isEmpty()) {
			readLine();
			buff.add("");
		}
		return buff.remove();
	}
}

class Interpreter
{
	VM vm;
	Function defFunc;
	TxtInput input;

	boolean execWord(String word)
	{
		var isExec = vm.mode == Mode.Execute;
		int[] id = new int[] {0};
		var ptr = vm.findFunc(word, id);

		if (ptr != null) {
			if (ptr.immediate) {
				ptr.run();
			} else if (isExec) {
				vm.callFunc(id[0]);
				return ptr.code.isEmpty();
			} else {
				defFunc.pushOpcode(OpCode.Call.ordinal(), id[0]);
			}
		} else {
			try {
				var num = Integer.valueOf(word);
				if (isExec) {
					vm.dstack.add(num);
				} else {
					defFunc.pushOpcode(OpCode.PushNum.ordinal(), num);
				}
			} catch (NumberFormatException e) {
				input.printError("Error: `" + word + "` not a function or a number");
			}
		}
		return true;
	}

	boolean read()
	{
		var word = input.nextWord();
		if (word.isEmpty()) {
			return false;
		}
		return execWord(word);
	}

	void prompt()
	{
		if (input.empty()) {
			System.out.print(vm.mode == Mode.Execute ? "\n> " : "...> ");
		}

		while (read()) {}
	}

	void endDefineFunc()
	{
		vm.mode = Mode.Execute;
		if (defFunc != null) {
			defFunc.pushInt(OpCode.Return.ordinal());
			defFunc = null;
		}
	}

	void defineFunc()
	{
		vm.mode = Mode.Compile;
		var name = input.nextWord();
		while (name.isEmpty()) {
			prompt();
			name = input.nextWord();
		}

		int[] id = new int[] {0};
		var ptr = vm.findFunc(name, id);
		if (ptr != null) {
			endDefineFunc();
			input.printError("Function already defined: " + name);
			return;
		}

		defFunc = vm.ftable.get(vm.addFunc(name));
	}

	void run()
	{
		while (vm.runVM) {
			switch (OpCode.valueOf(vm.nextOpcode())) {
			case Call:
				vm.call();
				break;
			case Jump:
				vm.jump();
				break;
			case Prompt:
				prompt();
				break;
			case PushNum:
				vm.pushNum();
				break;
			case Read:
				read();
				break;
			case Return:
				vm.returnFunc();
				break;
			default:
				var curr = vm.currOpcode();
				System.out.println("Invalid Opcode: " + curr + "; At Instruction: " + vm.pc);
			}
		}
	}

	Interpreter(VM vm, TxtInput input)
	{
		this.vm = vm;
		this.input = input;
		defFunc = null;

		var id = vm.addFunc(" ");
		var ptr = vm.ftable.get(id);
		ptr.pushInt(OpCode.Prompt.ordinal());
		ptr.pushOpcode(OpCode.Jump.ordinal(), -2);
		vm.fc = id;

		id = vm.addFunc(":");
		ptr = vm.ftable.get(id);
		ptr.callback = () -> defineFunc();

		id = vm.addFunc(";");
		ptr = vm.ftable.get(id);
		ptr.callback = () -> endDefineFunc();
		ptr.immediate = true;
	}
}
