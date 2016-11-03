#include<stdio.h>
#include<stdlib.h>
size_t *baseHeap;
size_t *topStack;
void init_hw1() {
	fprintf(stderr,"Initializing...\n");
	int i = 1;
	topStack = (size_t *)(&i-1);
	void *p = malloc(1000);
	baseHeap = (size_t *)(p-sizeof(size_t));
	free(p);
}

void check_memory() {
	void *maxHeap = sbrk(0);
	size_t* next = baseHeap;
	int heapSize = (maxHeap-(void*)baseHeap);
	int noAlloc = 0;
	int noFree = 0;
	int allocated = 0;
	int free = 0;
	int size = 0;
	int prevSize = *next&~0x1;
	int i=0;
	while((void*)next<maxHeap){
		int isAllocated = *next&0x1;
		size = *next&~0x1;
		if(i!=0 && next!=maxHeap){
			if(isAllocated){
				allocated+=prevSize;
				noAlloc++;
			}else{
				free+=prevSize;
				noFree++;
			}
			
		}else{

		}
		prevSize = size;
		next = ((void*)next)+size;
		i++;
	}
	int k = 1;
	void *newTopStack = (void *)(&k-1);
	int stackSize = ((void*)topStack-newTopStack);
	printf("%d %d %d %d %d %d\n",heapSize,noAlloc,noFree,allocated,free,stackSize);
}
