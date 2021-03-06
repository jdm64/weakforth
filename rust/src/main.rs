use std::collections::VecDeque;
use std::io::stdin;
use std::io::stdout;
use std::io::Write;

enum Callback {
	VM(fn(&mut VM), *mut VM),
	Interpreter(fn(&mut Interpreter), *mut Interpreter),
	Null,
}

struct Function {
	name: String,
	code: Vec<i8>,
	callback: Callback,
	immediate: bool,
}

impl Function {
	fn push_int(&mut self, val: i8) {
		self.code.push(val);
	}

	fn push_opcode(&mut self, opcode: i8, val: i8) {
		self.code.push(opcode);
		self.code.push(val);
	}

	fn run(&self) {
		match self.callback {
			Callback::VM(c, p) => unsafe { c(&mut *p) },
			Callback::Interpreter(c, p) => unsafe { c(&mut *p) },
			Callback::Null => {}
		}
	}
}

struct FunctionTable {
	items: Vec<Function>,
}

impl FunctionTable {
	fn new() -> FunctionTable {
		FunctionTable { items: vec![] }
	}

	fn get(&mut self, id: usize) -> Option<&mut Function> {
		self.items.get_mut(id)
	}

	fn find(&self, name: String, id: &mut usize) -> Option<&Function> {
		for i in 0..self.items.len() {
			if name == self.items[i].name {
				*id = i;
				return self.items.get(i);
			}
		}
		None
	}

	fn add(&mut self, name: String) -> usize {
		let f = Function {
			name: name,
			code: vec![],
			callback: Callback::Null,
			immediate: false,
		};
		self.items.push(f);
		self.items.len() - 1
	}

	fn set_callback(&mut self, id: usize, call: Callback) {
		self.items[id].callback = call;
	}
}

#[derive(Copy, Clone, PartialEq)]
enum OpCode {
	Call,
	Jump,
	Prompt,
	PushNum,
	Read,
	Return,
}

impl OpCode {
	fn from_i8(val: i8) -> Option<OpCode> {
		let v: Vec<OpCode> = vec![
			OpCode::Call,
			OpCode::Jump,
			OpCode::Prompt,
			OpCode::PushNum,
			OpCode::Read,
			OpCode::Return,
		];

		for i in v.into_iter() {
			if val == i as i8 {
				return Some(i);
			}
		}
		None
	}
}

#[derive(PartialEq)]
enum Mode {
	Execute,
	Compile,
}

struct ReturnData {
	id: usize,
	pc: isize,
}

struct VM {
	ftable: FunctionTable,
	dstack: Vec<i32>,
	rstack: Vec<ReturnData>,
	fc: usize,
	pc: isize,
	mode: Mode,
	run_vm: bool,
}

impl VM {
	fn new() -> VM {
		VM {
			ftable: FunctionTable::new(),
			dstack: vec![],
			rstack: vec![],
			fc: 0,
			pc: -1,
			mode: Mode::Execute,
			run_vm: true,
		}
	}

	fn get_opcode(&mut self, idx: usize) -> i8 {
		self.ftable.get(self.fc).unwrap().code[idx]
	}

	fn curr_opcode(&mut self) -> i8 {
		self.get_opcode(self.pc as usize)
	}

	fn next_opcode(&mut self) -> i8 {
		self.pc += 1;
		self.get_opcode(self.pc as usize)
	}

	fn next_int(&mut self) -> i32 {
		self.next_opcode() as i32
	}

	fn find_func(&self, name: String, id: &mut usize) -> Option<&Function> {
		self.ftable.find(name, id)
	}

	fn add_func(&mut self, name: String) -> usize {
		self.ftable.add(name)
	}

	fn call_func(&mut self, func: usize) {
		let ptr = self.ftable.get(func).unwrap();
		if !ptr.code.is_empty() {
			self.rstack.push(ReturnData {
				id: self.fc,
				pc: self.pc,
			});
			self.fc = func;
			self.pc = -1;
		} else {
			ptr.run();
		}
	}

	fn call(&mut self) {
		let id = self.next_int() as usize;
		self.call_func(id);
	}

	fn jump(&mut self) {
		self.pc += (self.next_int() - 1) as isize;
	}

	fn push_num(&mut self) {
		let num = self.next_int();
		self.dstack.push(num);
	}

	fn return_func(&mut self) {
		let data = self.rstack.pop().unwrap();
		self.fc = data.id;
		self.pc = data.pc;
	}

	fn exit(&mut self) {
		self.run_vm = false;
	}

	fn stack_add(&mut self) {
		let a = self.dstack.pop().unwrap();
		let b = self.dstack.pop().unwrap();
		self.dstack.push(b + a);
	}

	fn stack_sub(&mut self) {
		let a = self.dstack.pop().unwrap();
		let b = self.dstack.pop().unwrap();
		self.dstack.push(b - a);
	}

	fn stack_mul(&mut self) {
		let a = self.dstack.pop().unwrap();
		let b = self.dstack.pop().unwrap();
		self.dstack.push(b * a);
	}

	fn stack_div(&mut self) {
		let a = self.dstack.pop().unwrap();
		let b = self.dstack.pop().unwrap();
		self.dstack.push(b / a);
	}

	fn stack_dup(&mut self) {
		let val = *self.dstack.last().unwrap();
		self.dstack.push(val);
	}

	fn stack_pop(&mut self) {
		self.dstack.pop();
	}

	fn stack_clr(&mut self) {
		self.dstack.clear();
	}

	fn stack_swp(&mut self) {
		let a = self.dstack.pop().unwrap();
		let b = self.dstack.pop().unwrap();
		self.dstack.push(a);
		self.dstack.push(b);
	}

	fn print_top_stack(&mut self) {
		if !self.dstack.is_empty() {
			let s = (*self.dstack.last().unwrap()).to_string();
			println!("{}", s);
		} else {
			println!("<empty>");
		}
	}

	fn print_stack(&mut self) {
		print!("[ ");
		for item in self.dstack.iter() {
			print!("{} ", item.to_string());
		}
		println!("]");
	}

	fn add_callback_any(&mut self, name: String, callback: Callback) -> usize {
		let id = self.add_func(name);
		self.ftable.set_callback(id, callback);
		id
	}

	fn add_callback_vm(&mut self, name: String, ptr: fn(&mut VM)) -> usize {
		let callback = Callback::VM(ptr, self as *mut VM);
		self.add_callback_any(name, callback)
	}

	fn setup_functions(&mut self) {
		self.add_callback_vm(".".to_string(), VM::print_top_stack);
		self.add_callback_vm("..".to_string(), VM::print_stack);

		self.add_callback_vm("+".to_string(), VM::stack_add);
		self.add_callback_vm("-".to_string(), VM::stack_sub);
		self.add_callback_vm("*".to_string(), VM::stack_mul);
		self.add_callback_vm("/".to_string(), VM::stack_div);

		self.add_callback_vm("dup".to_string(), VM::stack_dup);
		self.add_callback_vm("pop".to_string(), VM::stack_pop);
		self.add_callback_vm("clr".to_string(), VM::stack_clr);
		self.add_callback_vm("swp".to_string(), VM::stack_swp);
		self.add_callback_vm("exit".to_string(), VM::exit);
	}
}

struct TxtInput {
	buff: VecDeque<String>,
}

impl TxtInput {
	fn new() -> TxtInput {
		TxtInput {
			buff: VecDeque::new(),
		}
	}

	fn empty(&self) -> bool {
		self.buff.is_empty()
	}

	fn print_error(&mut self, msg: String) {
		print!("{}", msg);
		self.buff.clear();
		self.buff.push_back("".to_string());
	}

	fn read_line(&mut self) {
		let mut line = String::new();

		let _r = stdin().read_line(&mut line);

		let words = line.split_whitespace();
		for word in words {
			self.buff.push_back(word.to_string());
		}
	}

	fn next_word(&mut self) -> Option<String> {
		if self.buff.is_empty() {
			self.read_line();
			self.buff.push_back("".to_string());
		}
		self.buff.pop_front()
	}
}

struct Interpreter {
	vm: Box<VM>,
	def_id: Option<usize>,
	input: Box<TxtInput>,
}

impl Interpreter {
	fn get_def_func(&mut self) -> &mut Function {
		self.vm.ftable.get(self.def_id.unwrap()).unwrap()
	}

	fn exec_word(&mut self, word: String) -> bool {
		let is_exec = self.vm.mode == Mode::Execute;
		let mut id: usize = 0;
		let ptr = self.vm.find_func(word.clone(), &mut id);

		match ptr {
			None => match word.parse::<i32>() {
				Err(_e) => {
					self.input.print_error(format!(
						"Error: `{}` not a function or a number",
						word
					));
				}
				Ok(val) => {
					if is_exec {
						self.vm.dstack.push(val);
					} else {
						self.get_def_func().push_opcode(
							OpCode::PushNum as i8,
							val as i8,
						);
					}
				}
			},
			Some(func) => {
				if func.immediate {
					func.run();
				} else if is_exec {
					let no_code = func.code.is_empty();
					self.vm.call_func(id);
					return no_code;
				} else {
					self.get_def_func()
						.push_opcode(OpCode::Call as i8, id as i8);
				}
			}
		}
		true
	}

	fn read(&mut self) -> bool {
		let word = self.input.next_word().unwrap();
		if word.is_empty() {
			return false;
		}
		self.exec_word(word)
	}

	fn prompt(&mut self) {
		if self.input.empty() {
			let msg = match self.vm.mode {
				Mode::Execute => "\n> ",
				_ => "...> ",
			};
			print!("{}", msg);
			let _e = stdout().flush();
		}

		while self.read() {}
	}

	fn end_define_func(&mut self) {
		self.vm.mode = Mode::Execute;
		if self.def_id.is_some() {
			self.get_def_func().push_int(OpCode::Return as i8);
			self.def_id = None;
		}
	}

	fn define_func(&mut self) {
		self.vm.mode = Mode::Compile;
		let mut name = self.input.next_word().unwrap();
		while name.is_empty() {
			self.prompt();
			name = self.input.next_word().unwrap();
		}

		let mut id: usize = 0;
		let ptr = self.vm.find_func(name.clone(), &mut id);
		if ptr.is_some() {
			self.end_define_func();
			self.input
				.print_error(format!("Function already defined: {}", name));
			return;
		}

		self.def_id = Some(self.vm.add_func(name));
	}

	fn run(&mut self) {
		while self.vm.run_vm {
			match OpCode::from_i8(self.vm.next_opcode()) {
				Some(OpCode::Call) => {
					self.vm.call();
				}
				Some(OpCode::Jump) => {
					self.vm.jump();
				}
				Some(OpCode::Prompt) => {
					self.prompt();
				}
				Some(OpCode::PushNum) => {
					self.vm.push_num();
				}
				Some(OpCode::Read) => {
					self.read();
				}
				Some(OpCode::Return) => {
					self.vm.return_func();
				}
				None => println!(
					"Invalid Opcode: {}; At Instruction: {}",
					self.vm.curr_opcode(),
					self.vm.pc
				),
			}
		}
	}

	fn setup(&mut self) {
		self.vm.setup_functions();

		let mut id = self.vm.add_func(" ".to_string());
		let ptr = self.vm.ftable.get(id).unwrap();
		ptr.push_int(OpCode::Prompt as i8);
		ptr.push_opcode(OpCode::Jump as i8, -2);
		self.vm.fc = id;

		let mut cb = Callback::Interpreter(Interpreter::define_func, self);
		self.vm.add_callback_any(":".to_string(), cb);

		cb = Callback::Interpreter(Interpreter::end_define_func, self);
		id = self.vm.add_callback_any(";".to_string(), cb);
		self.vm.ftable.get(id).unwrap().immediate = true;
	}

	fn new(vm: Box<VM>, input: Box<TxtInput>) -> Interpreter {
		Interpreter {
			vm: vm,
			input: input,
			def_id: None,
		}
	}
}

fn main() {
	let vm = Box::new(VM::new());
	let input = Box::new(TxtInput::new());
	let mut runner = Interpreter::new(vm, input);

	runner.setup();
	runner.run();
}
