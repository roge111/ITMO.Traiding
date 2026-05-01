#include <linux/module.h>
#include <linux/export-internal.h>
#include <linux/compiler.h>

MODULE_INFO(name, KBUILD_MODNAME);

__visible struct module __this_module
__section(".gnu.linkonce.this_module") = {
	.name = KBUILD_MODNAME,
	.init = init_module,
#ifdef CONFIG_MODULE_UNLOAD
	.exit = cleanup_module,
#endif
	.arch = MODULE_ARCH_INIT,
};



static const struct modversion_info ____versions[]
__used __section("__versions") = {
	{ 0xd272d446, "__x86_return_thunk" },
	{ 0xd272d446, "__stack_chk_fail" },
	{ 0x4b5cc7c5, "kernel_read" },
	{ 0xc609ff70, "strncpy" },
	{ 0x7f79e79a, "kthread_create_on_node" },
	{ 0x630dad60, "wake_up_process" },
	{ 0x90a48d82, "__ubsan_handle_out_of_bounds" },
	{ 0x0571dc46, "kthread_stop" },
	{ 0xc01aafd2, "get_random_u32" },
	{ 0x224a53e7, "get_random_bytes" },
	{ 0x680628e7, "ktime_get_real_ts64" },
	{ 0x7fd36f2e, "time64_to_tm" },
	{ 0x40a621c5, "snprintf" },
	{ 0x9479a1e8, "strnlen" },
	{ 0x23ef80fb, "vfs_llseek" },
	{ 0xd710adbf, "__kmalloc_noprof" },
	{ 0x43a349ca, "strlen" },
	{ 0xcb8b6ec6, "kfree" },
	{ 0xe54e0a6b, "__fortify_panic" },
	{ 0x67628f51, "msleep" },
	{ 0x5e505530, "kthread_should_stop" },
	{ 0xd272d446, "__fentry__" },
	{ 0xbd03ed67, "__ref_stack_chk_guard" },
	{ 0x1cae9a42, "filp_open" },
	{ 0xdbb4ec87, "kernel_write" },
	{ 0x9d52f437, "filp_close" },
	{ 0xe8213e80, "_printk" },
	{ 0xbebe66ff, "module_layout" },
};

static const u32 ____version_ext_crcs[]
__used __section("__version_ext_crcs") = {
	0xd272d446,
	0xd272d446,
	0x4b5cc7c5,
	0xc609ff70,
	0x7f79e79a,
	0x630dad60,
	0x90a48d82,
	0x0571dc46,
	0xc01aafd2,
	0x224a53e7,
	0x680628e7,
	0x7fd36f2e,
	0x40a621c5,
	0x9479a1e8,
	0x23ef80fb,
	0xd710adbf,
	0x43a349ca,
	0xcb8b6ec6,
	0xe54e0a6b,
	0x67628f51,
	0x5e505530,
	0xd272d446,
	0xbd03ed67,
	0x1cae9a42,
	0xdbb4ec87,
	0x9d52f437,
	0xe8213e80,
	0xbebe66ff,
};
static const char ____version_ext_names[]
__used __section("__version_ext_names") =
	"__x86_return_thunk\0"
	"__stack_chk_fail\0"
	"kernel_read\0"
	"strncpy\0"
	"kthread_create_on_node\0"
	"wake_up_process\0"
	"__ubsan_handle_out_of_bounds\0"
	"kthread_stop\0"
	"get_random_u32\0"
	"get_random_bytes\0"
	"ktime_get_real_ts64\0"
	"time64_to_tm\0"
	"snprintf\0"
	"strnlen\0"
	"vfs_llseek\0"
	"__kmalloc_noprof\0"
	"strlen\0"
	"kfree\0"
	"__fortify_panic\0"
	"msleep\0"
	"kthread_should_stop\0"
	"__fentry__\0"
	"__ref_stack_chk_guard\0"
	"filp_open\0"
	"kernel_write\0"
	"filp_close\0"
	"_printk\0"
	"module_layout\0"
;

MODULE_INFO(depends, "");


MODULE_INFO(srcversion, "5832795B6D21561B894695F");
