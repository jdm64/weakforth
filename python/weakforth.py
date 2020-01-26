#!/usr/bin/env python3

import sys
from enum import Enum
from collections import deque

def toNumber(num):
	try:
		return int(num)
	except:
		try:
			return float(num)
		except:
			return None

OpCode = Enum('OpCode', 'Call Jump Prompt PushNum Read Return')

Mode = Enum('Mode', 'Compile Execute')

class FunctionItem:
	def __init__(self, name, run=None, code=None, immediate=False):
		self.name = name
		self.run = run
		self.code = code
		self.immediate = immediate if run else False

class BaseVM:
	def __init__(self):
		self.runVM = True
		self.ftable = []
		self.dstack = []
		self.rstack = []
		self.currFunc = None
		self.currFuncId = 0
		self.pc = -1
		self.mode = Mode.Execute

		self.opTable = {
			OpCode.Call    : self.call,
			OpCode.Jump    : self.jump,
			OpCode.PushNum : self.pushNum,
			OpCode.Return  : self.returnFunc,
		}

	##################
	# Util functions #
	##################

	def currInst(self):
		return self.currFunc.code[self.pc]

	def nextInst(self):
		self.pc += 1
		return self.currFunc.code[self.pc]

	def findFunc(self, name):
		funcs = [[i, x] for i, x in enumerate(self.ftable) if x.name == name]
		return funcs[0] if funcs else [None, None]

	def addFunc(self, name, run=None, code=None, immediate=False):
		func = FunctionItem(name, run, code, immediate)
		self.ftable.append(func)
		return [len(self.ftable) - 1, func]

	def callFunc(self, i, func):
		if func.code:
			self.rstack.append([self.currFuncId, self.pc])
			self.currFunc = func
			self.currFuncId = i
			self.pc = -1
		else:
			func.run()

	####################
	# OpCode Functions #
	####################

	def call(self):
		i = self.nextInst()
		func = self.ftable[i]
		self.callFunc(i, func)

	def jump(self):
		self.pc += self.nextInst()

	def pushNum(self):
		self.dstack.append(self.nextInst())

	def returnFunc(self):
		self.currFuncId, self.pc = self.rstack.pop()
		self.currFunc = self.ftable[self.currFuncId]

class VM(BaseVM):
	def __init__(self):
		super().__init__()
		self.setupFunctions()

	def exit(self):
		self.runVM = False

	def stackDup(self):
		self.dstack.append(self.dstack[-1])

	def stackAdd(self):
		a, b = self.dstack.pop(), self.dstack.pop()
		self.dstack.append(b + a)

	def stackSub(self):
		a, b = self.dstack.pop(), self.dstack.pop()
		self.dstack.append(b - a)

	def stackMul(self):
		a, b = self.dstack.pop(), self.dstack.pop()
		self.dstack.append(b * a)

	def stackDiv(self):
		a, b = self.dstack.pop(), self.dstack.pop()
		self.dstack.append(b / a)

	def stackPop(self):
		self.dstack.pop()

	def stackClr(self):
		self.dstack.clear()

	def stackSwp(self):
		a, b = self.dstack.pop(), self.dstack.pop()
		self.dstack.extend([a, b])

	def printTopStack(self):
		if self.dstack:
			print(self.dstack[-1])
		else:
			print(None)

	def printFullStack(self):
		print(self.dstack)

	def setupFunctions(self):
		self.addFunc('.' , self.printTopStack)
		self.addFunc('..', self.printFullStack)
		self.addFunc('+' , self.stackAdd)
		self.addFunc('-' , self.stackSub)
		self.addFunc('*' , self.stackMul)
		self.addFunc('/' , self.stackDiv)
		self.addFunc('dup', self.stackDup)
		self.addFunc('pop' , self.stackPop)
		self.addFunc('clr' , self.stackClr)
		self.addFunc('swp' , self.stackSwp)
		self.addFunc('exit', self.exit)

class UserInput:
	def __init__(self):
		self.buff = deque()

	def hasData(self):
		return bool(self.buff)

	def prependEmpty(self):
		self.buff.insert(0, '')

	def printError(self, msg):
		print(msg)
		self.buff.clear()
		self.buff.append('')

	def nextWord(self):
		if not self.buff:
			self.buff.extend(input().split())
			self.buff.append('')
		return self.buff.popleft()

class TextInput:
	def __init__(self, filename):
		self.buff = deque()
		with open(filename, 'r') as f:
			first = f.readline()
			if not first.startswith("#!"):
				words = first.split()
				if words:
					self.buff.extend(words)
			self.buff.extend(f.read().split())
			self.buff.append('exit')

	def hasData(self):
		return bool(self.buff)

	def prependEmpty(self):
		self.buff.insert(0, '')

	def printError(self, msg):
		print(msg)
		self.buff.clear()
		self.buff.append('exit')

	def nextWord(self):
		return self.buff.popleft()

class Interpreter:
	def __init__(self, vm, filename):
		self.vm = vm
		self.vm.opTable[OpCode.Prompt] = self.prompt
		self.vm.opTable[OpCode.Read] = self.read

		if filename:
			self.txtInput = TextInput(filename)
			mainCode = [OpCode.Read, OpCode.Jump, -2]
		else:
			self.txtInput = UserInput()
			mainCode = [OpCode.Prompt, OpCode.Jump, -2]

		self.vm.currFuncId, self.vm.currFunc = self.vm.addFunc(' ', code=mainCode)
		self.vm.addFunc(':' , self.defineFunc)
		self.vm.addFunc(';' , self.endDefineFunc, immediate=True)

		self.defFunc = None

	def invalidInst(self):
		self.txtInput.printError("Invalid instruction: " + self.currInst())

	def execWord(self, word):
		i, func = self.vm.findFunc(word)
		if not func:
			num = toNumber(word)
			if num == None:
				self.txtInput.printError("Error: `" + word + "` not a function or a number")
				return

			if self.vm.mode == Mode.Compile:
				self.defFunc.code.extend([OpCode.PushNum, num])
			else:
				self.vm.dstack.append(num)
			return

		if func.immediate:
			func.run()
		elif self.vm.mode == Mode.Execute:
			self.vm.callFunc(i, func)
			if func.code:
				self.txtInput.prependEmpty()
		else:
			self.defFunc.code.extend([OpCode.Call, i])

	def defineFunc(self):
		self.vm.mode = Mode.Compile
		name = self.txtInput.nextWord()
		while not name:
			self.prompt()
			name = self.txtInput.nextWord()
		_, func = self.vm.findFunc(name)
		if func:
			self.endDefineFunc()
			self.txtInput.printError("Function already defined: " + name)
			return
		_, self.defFunc = self.vm.addFunc(name, code=[])

	def endDefineFunc(self):
		self.vm.mode = Mode.Execute
		if self.defFunc:
			self.defFunc.code.append(OpCode.Return)
			self.defFunc = None

	def read(self):
		word = self.txtInput.nextWord()
		if not word:
			return False
		self.execWord(word)
		return True

	def prompt(self):
		if not self.txtInput.hasData():
			print('\n> ' if self.vm.mode == Mode.Execute else '...> ', end='')
		while self.read():
			pass

	def run(self):
		while self.vm.runVM:
			self.vm.opTable.get(self.vm.nextInst(), self.invalidInst)()

if __name__ == "__main__":
	vm = VM()

	if len(sys.argv) > 1:
		filename = sys.argv[1]
	else:
		filename = None

	Interpreter(vm, filename).run()
