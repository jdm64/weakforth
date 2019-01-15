#!/usr/bin/env python3

from enum import Enum
from collections import deque

OpCode = Enum('OpCode', 'Call Jump Prompt PushNum Return')

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
		self.defFunc = None
		self.currFunc = None
		self.currFuncId = 0
		self.pc = -1
		self.mode = Mode.Execute
		self.buff = deque()

		self.opTable = {
			OpCode.Call    : self.call,
			OpCode.Jump    : self.jump,
			OpCode.Prompt  : self.prompt,
			OpCode.PushNum : self.pushNum,
			OpCode.Return  : self.returnFunc,
		}

		self.setupFunctions()

	##################
	# Util functions #
	##################

	def toNumber(self, num):
		try:
			return int(num)
		except:
			try:
				return float(num)
			except:
				return None

	def currInst(self):
		return self.currFunc.code[self.pc]

	def nextInst(self):
		self.pc += 1
		return self.currFunc.code[self.pc]

	def printError(self, msg):
		print(msg)
		self.buff.clear()
		self.buff.append('')

	def invalidInst(self):
		self.printError("Invalid instruction: " + self.currInst())

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

	def nextWord(self):
		if not self.buff:
			self.buff.extend(input().split())
			self.buff.append('')
		return self.buff.popleft()

	def execWord(self, word):
		i, func = self.findFunc(word)
		if not func:
			num = self.toNumber(word)
			if num == None:
				self.printError("Error: `" + word + "` not a function or a number")
				return

			if self.mode == Mode.Compile:
				self.defFunc.code.extend([OpCode.PushNum, num])
			else:
				self.dstack.append(num)
			return

		if func.immediate:
			func.run()
		elif self.mode == Mode.Execute:
			self.callFunc(i, func)
			if func.code:
				self.buff.insert(0, '')
		else:
			self.defFunc.code.extend([OpCode.Call, i])

	####################
	# OpCode Functions #
	####################

	def call(self):
		i = self.nextInst()
		func = self.ftable[i]
		self.callFunc(i, func)

	def jump(self):
		self.pc += self.nextInst()

	def prompt(self):
		if not self.buff:
			print('\n> ' if self.mode == Mode.Execute else '...> ', end='')
		while True:
			word = self.nextWord()
			if not word:
				return
			self.execWord(word)

	def pushNum(self):
		self.dstack.append(self.nextInst())

	def returnFunc(self):
		self.currFuncId, self.pc = self.rstack.pop()
		self.currFunc = self.ftable[self.currFuncId]

	#########################
	# Setup & Run Functions #
	#########################

	def setupFunctions(self):
		# overload this function to add functions
		pass

	def runInterpreter(self):
		mainCode = [OpCode.Prompt, OpCode.Jump, -2]
		self.currFuncId, self.currFunc = self.addFunc(' ', code=mainCode)

		while self.runVM:
			self.opTable.get(self.nextInst(), self.invalidInst)()

class VM(BaseVM):
	def __init__(self):
		super().__init__()

	def exit(self):
		self.runVM = False

	def defineFunc(self):
		self.mode = Mode.Compile
		name = self.nextWord()
		while not name:
			self.prompt()
			name = self.nextWord()
		_, func = self.findFunc(name)
		if func:
			self.endDefineFunc()
			self.printError("Function already defined: " + name)
			return
		_, self.defFunc = self.addFunc(name, code=[])

	def endDefineFunc(self):
		self.mode = Mode.Execute
		if self.defFunc:
			self.defFunc.code.append(OpCode.Return)
			self.defFunc = None

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
		self.addFunc(':' , self.defineFunc)
		self.addFunc(';' , self.endDefineFunc, immediate=True)
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

if __name__ == "__main__":
	vm = VM()
	vm.runInterpreter()
