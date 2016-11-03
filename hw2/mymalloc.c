#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

typedef struct chunk
{
	size_t size;
	struct chunk* prev;
	struct chunk* next;
}freechunk;

freechunk* freeList = 0;

size_t* bottom;
freechunk* top;

#define MIN_CHUNK_SIZE 4*sizeof(size_t)
#define getSize(b) (b->size & ~1)
#define in_use(a)(((freechunk*)(((char*)a)+getSize(a)))->size&1)

int init_mymalloc(){
	bottom = sbrk(0);
	if(((int)sbrk(MIN_CHUNK_SIZE))==-1){
		printf("Could not allocate space \n");
		exit(-1);
	}
	top = (freechunk*)bottom;
	top->size = MIN_CHUNK_SIZE;
	top->prev = 0;
	top->next = 0;
	//freeList = top;
}

freechunk* bestfit(int size){
	freechunk* bestChunk = 0;
	freechunk* iter = freeList;
	while(iter->next){
		if(getSize(iter)>size&&(!bestChunk||getSize(bestChunk)>getSize(iter))){
			bestChunk = iter;
		}
		iter = iter->next;
	}
	if(!bestChunk){
		bestChunk = top;
	}
	return bestChunk;
}

void* mymalloc(int size){
	int chunk_size = sizeof(size_t)+size;
	if(chunk_size%8){
		chunk_size+=(8-chunk_size%8);
	}

	freechunk* existingChunk = bestfit(chunk_size);
	remove_from_free(existingChunk);
	// it was top and we need to expand heap
	if(getSize(existingChunk)<chunk_size || (existingChunk==top && getSize(existingChunk)-chunk_size<=MIN_CHUNK_SIZE)){
		if(((int)sbrk(chunk_size))==-1){
			printf("Could not allocate space \n");
			exit(-1);
		}
		freechunk* myChunk = top;
		printf("%zu \n", myChunk->size);
		size_t topSize = top->size|0x1;
		freechunk* prev = top->prev;
		top = ((char*)myChunk+chunk_size);
		top->size = topSize;
		top->prev = prev;
		top->next = 0;
		myChunk->size = chunk_size|1;
		//add_to_free(top);
		return ((char*)myChunk+sizeof(size_t));
	}
	// we can break the chunk so no issue
	else if(getSize(existingChunk)-chunk_size>MIN_CHUNK_SIZE){
		freechunk* brokenChunk = (char*)existingChunk+chunk_size;
		brokenChunk->size = existingChunk->size - chunk_size|1;
		size_t* footer = (char*)brokenChunk+getSize(brokenChunk)-sizeof(size_t);
		*footer = brokenChunk->size; 
		// this should not happen
		if(existingChunk==top){
			top = brokenChunk;
			top->next = 0;
		}
		add_to_free(brokenChunk);
		existingChunk->size = chunk_size|1;
	}else{
		
		existingChunk->next->size = existingChunk->next->size|1;
	}
	return ((char*)existingChunk+sizeof(size_t));
}



void add_to_free(freechunk* chunk){
	if(chunk==top){
		return;
	}
	chunk->next = freeList;
	if(freeList)
		freeList->prev = chunk;
	freeList = chunk;
}

void remove_from_free(freechunk* chunk){
	if(chunk==top)
		return;
	if(chunk->prev){
		chunk->prev->next = chunk->next;
	}else{
		freeList = chunk->next;
	}
}

void myfree(char* address){
	freechunk* chunk = (freechunk*)(address-sizeof(size_t));
	// if next is free
	freechunk* next = (freechunk*)(address - sizeof(size_t)+getSize(chunk));
	if(next==top || !in_use(next)){
		if(next==top){
			top = chunk;
		}
		remove_from_free(next);
		chunk->size = chunk->size+getSize(next);
	}
	// if previous exist
	if(address-sizeof(size_t)>bottom){
		// if previous is free
		if(!chunk->size&1){			
			freechunk* header = address-2*sizeof(size_t);
			freechunk* prev = address-sizeof(size_t)-getSize(header);
			remove_from_free(prev);
			prev->size = getSize(prev)+getSize(chunk)+1;
			if(top==chunk){
				top = prev;
			}
			chunk = prev;
		}
	}
	*((size_t*)((char*)chunk+getSize(chunk)-sizeof(size_t))) = chunk->size;
	freechunk* cur_next = (freechunk*)(((char*)chunk)+getSize(chunk));
	cur_next->size = cur_next->size&~1;
	add_to_free(chunk);
	if(top->size>MIN_CHUNK_SIZE){
		if(((int)sbrk(MIN_CHUNK_SIZE - getSize(top)))==-1){
			printf("Could not lower space \n");
			exit(-1);
		}
		top->size = MIN_CHUNK_SIZE+1;
	}
}

// int main(int argc,char** argv){
// 	init_mymalloc();
// 	int *someval = mymalloc(400);
// 	int *anotherval = mymalloc(700);
// 	int *anotherOne = mymalloc(600);
// 	int *fourth = mymalloc(900);
// 	int *fifth = mymalloc(800);
// 	myfree(anotherval);
// 	myfree(fourth);
// 	int *sixth = mymalloc(500);
// 	int *seventh = mymalloc(174);
// 	printf("%x %x\n",(int*)someval,(int*)anotherval);
// 	return 0;
// }

