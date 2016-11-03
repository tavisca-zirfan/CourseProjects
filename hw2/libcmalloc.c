/* this is just a minimal wrapper around the libc malloc */

#include<stdlib.h>

void init_mymalloc() {	
}

void* mymalloc(size_t size) {
	return malloc(size);
}

void myfree(void* ptr) {
	free(ptr);
}
