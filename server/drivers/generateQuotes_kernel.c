// SPDX-License-Identifier: GPL-2.0
/*
 * Символьный драйвер-имитатор биржи.
 *
 * Устройство /dev/itmo_quotes хранит массив последних котировок.
 * Пользовательская программа получает снимок массива через open/read/close.
 */

#include <linux/delay.h>
#include <linux/fs.h>
#include <linux/kernel.h>
#include <linux/kthread.h>
#include <linux/miscdevice.h>
#include <linux/module.h>
#include <linux/mutex.h>
#include <linux/random.h>
#include <linux/slab.h>
#include <linux/string.h>
#include <linux/timekeeping.h>
#include <linux/uaccess.h>

#define DEVICE_NAME "itmo_quotes"
#define STOCK_COUNT 7
#define SNAPSHOT_SIZE 1024

struct quote {
	char name[16];
	u32 price;
	time64_t updated_at;
};

static const char *const stock_names[STOCK_COUNT] = {
	"GAZ", "YANDEX", "SBER", "LUKOIL", "ROSNEFT", "VTBR", "MOEX"
};

static struct quote quotes[STOCK_COUNT];
static DEFINE_MUTEX(quotes_lock);
static struct task_struct *generator_thread;

static u32 random_price(void)
{
	return 1000 + get_random_u32_below(499001);
}

static void update_random_quote(void)
{
	u32 index = get_random_u32_below(STOCK_COUNT);

	mutex_lock(&quotes_lock);
	quotes[index].price = random_price();
	quotes[index].updated_at = ktime_get_real_seconds();
	mutex_unlock(&quotes_lock);
}

static int generator(void *unused)
{
	while (!kthread_should_stop()) {
		update_random_quote();
		msleep_interruptible(3000);
	}
	return 0;
}

static int quotes_open(struct inode *inode, struct file *file)
{
	char *snapshot;
	size_t length = 0;
	int i;

	snapshot = kzalloc(SNAPSHOT_SIZE, GFP_KERNEL);
	if (!snapshot)
		return -ENOMEM;

	mutex_lock(&quotes_lock);
	for (i = 0; i < STOCK_COUNT; ++i) {
		struct tm timestamp;

		time64_to_tm(quotes[i].updated_at, 0, &timestamp);
		length += scnprintf(
			snapshot + length,
			SNAPSHOT_SIZE - length,
			"%s,%u,%04ld-%02d-%02d %02d:%02d:%02d\n",
			quotes[i].name,
			quotes[i].price,
			timestamp.tm_year + 1900,
			timestamp.tm_mon + 1,
			timestamp.tm_mday,
			timestamp.tm_hour,
			timestamp.tm_min,
			timestamp.tm_sec
		);
	}
	mutex_unlock(&quotes_lock);

	file->private_data = snapshot;
	return 0;
}

static ssize_t quotes_read(
	struct file *file,
	char __user *buffer,
	size_t count,
	loff_t *offset
)
{
	const char *snapshot = file->private_data;

	return simple_read_from_buffer(
		buffer,
		count,
		offset,
		snapshot,
		strlen(snapshot)
	);
}

static int quotes_release(struct inode *inode, struct file *file)
{
	kfree(file->private_data);
	file->private_data = NULL;
	return 0;
}

static const struct file_operations quotes_fops = {
	.owner = THIS_MODULE,
	.open = quotes_open,
	.read = quotes_read,
	.release = quotes_release,
	.llseek = no_llseek,
};

static struct miscdevice quotes_device = {
	.minor = MISC_DYNAMIC_MINOR,
	.name = DEVICE_NAME,
	.fops = &quotes_fops,
	.mode = 0444,
};

static int __init quotes_init(void)
{
	int i;
	int result;
	time64_t now = ktime_get_real_seconds();

	for (i = 0; i < STOCK_COUNT; ++i) {
		strscpy(quotes[i].name, stock_names[i], sizeof(quotes[i].name));
		quotes[i].price = random_price();
		quotes[i].updated_at = now;
	}

	result = misc_register(&quotes_device);
	if (result)
		return result;

	generator_thread = kthread_run(generator, NULL, "itmo_quotes_generator");
	if (IS_ERR(generator_thread)) {
		result = PTR_ERR(generator_thread);
		misc_deregister(&quotes_device);
		return result;
	}

	pr_info("itmo_quotes: device /dev/%s registered\n", DEVICE_NAME);
	return 0;
}

static void __exit quotes_exit(void)
{
	kthread_stop(generator_thread);
	misc_deregister(&quotes_device);
	pr_info("itmo_quotes: module unloaded\n");
}

module_init(quotes_init);
module_exit(quotes_exit);

MODULE_LICENSE("GPL");
MODULE_AUTHOR("ITMO Trading");
MODULE_DESCRIPTION("Character device that exposes an array of generated quotes");
