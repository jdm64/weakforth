
OBJS = Base.o cstring.o List.o Queue.o main.o
LL_FILES = Base.ll cstring.ll List.ll Queue.ll main.ll
BIN = weakforth

all : $(OBJS)
	clang $(OBJS) -o $(BIN)

clean :
	rm -f *.o *.ll $(BIN)*

%.o : %.syp
	saphyr $<

%.ll : %.syp
	saphyr --llvmir $<

debug-build : $(LL_FILES)
	llvm-link $(LL_FILES) -o $(BIN).ll
