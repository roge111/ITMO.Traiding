# Makefile для сборки модуля ядра generateQuotes_kernel.ko

obj-m := generateQuotes_kernel.o

KDIR ?= /lib/modules/$(shell uname -r)/build
PWD := $(shell pwd)

# Экранирование пути с пробелами
MODULE_DIR := "$(PWD)"

all:
	$(MAKE) -C $(KDIR) M=$(MODULE_DIR) modules

clean:
	$(MAKE) -C $(KDIR) M=$(MODULE_DIR) clean

install: all
	sudo insmod generateQuotes_kernel.ko

uninstall:
	sudo rmmod generateQuotes_kernel

.PHONY: all clean install uninstall