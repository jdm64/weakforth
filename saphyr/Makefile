
STD_DIR = ~/.local/share/saphyr/pkgs/std

OBJS = $(STD_DIR)/cstring.o main.o
LL_FILES = $(STD_DIR)/cstring.ll main.ll
BIN = weakforth

all : $(OBJS)
	clang $(OBJS) -o $(BIN)

clean :
	rm -f $(OBJS) $(LL_FILES) $(BIN)*

%.o : %.syp
	saphyr $<

%.ll : %.syp
	saphyr --llvmir $<

debug-build : $(LL_FILES)
	llvm-link $(LL_FILES) -o $(BIN).ll

install-libs :
	bash <(wget -qO- https://raw.githubusercontent.com/jdm64/saphyr-libs/master/setup.sh)
