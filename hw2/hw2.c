#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

typedef struct chunk {
	size_t size;
	struct chunk* next;
	struct chunk* prev;
} chunk;

char* top;
char* bottom;

#define chunk_size(chunk) (chunk->size & ~1)
#define in_use(c) ((((struct chunk*)(((char*)c)+chunk_size(c)))->size&1)==1)

#define COALESCE 1
#define SPLIT_LARGE 1
#define MIN_CHUNK_SIZE 4*sizeof(size_t)

//#define DEBUG 0

chunk *free_list = 0;

chunk *segmentedFreeList[30];
chunk *req_free_list = 0;

// the top chunk is special, just like in libc malloc. There is always a free top chunk, and its size is virtually unbounded 
// since we can always grow it. However, this increases memory usage, which is bad, so we should only use the top chunk if we really need it.

chunk *top_chunk = 0;

void check_free_list() {
	chunk *c = free_list;
	while(c) {
		if(in_use(c)) {
			printf("warning: in-use chunk in free list.\n");
		}
		c=c->next;
	}
}

void check_heap() {
	chunk *c = bottom;
	int chunks=0;
	int inuse=0;
	while(c!=top_chunk) {
		chunks++;
		if(in_use(c)) inuse++;
		else {		
			size_t back_size=*((size_t*)((char*)c+chunk_size(c)-sizeof(size_t)));
			if(chunk_size(c)!=back_size) {
				printf("mismatching free boundary tags: %zd, %zd\n",back_size,chunk_size(c));
			}
		}
		c=(char*)c+chunk_size(c);
	}
}

chunk* getFreeList(int size){
	int index = 0;
	int freeSize = 8;
	while(freeSize<size && index<29){
		freeSize=freeSize<<1;
		index++;
	}
	return segmentedFreeList[index];
}

void add_item(chunk* item) {

#ifdef DEBUG
	if(item==top_chunk) {
		printf("warning, adding top chunk to list!\n");
		return;
	}

	chunk* search=getFreeList(chunk_size(item));
	while(search && search!=item) {
		search=search->next;
	}
	if(search==item) {
		printf("WARNING: tried to add existing item to list!\n");
	}
#endif
	req_free_list = getFreeList(chunk_size(item));
	item->prev = 0;
	item->next=req_free_list;
	if(req_free_list)
		(req_free_list)->prev = item;
	req_free_list = item;

#ifdef DEBUG
	check_free_list();
	check_heap();
#endif
}

void unlink_item(chunk* item) {

#ifdef DEBUG
	check_free_list();
	check_heap();

	if(item==top_chunk) 
		printf("warning, pulling top chunk from list!\n");

	chunk* search=getFreeList(chunk_size(item));
	while(search && search!=item) {
		search=search->next;
	}
	if(search!=item) {
		printf("WARNING: tried to remove nonexisting item %p of size %d from list!\n",item,item->size);
		return;
	}
#endif

	req_free_list = getFreeList(chunk_size(item));
	if(item->prev == 0) 
		req_free_list = item->next;
	else	
		item->prev->next = item->next;

	if(item->next != 0)
		item->next->prev = item->prev;	

#ifdef DEBUG
	check_free_list();
	check_heap();
#endif
}

// find best fit chunk
chunk* best_fit(int size) {
	chunk *iter = getFreeList(size);
	chunk *best_chunk = 0;
	while(iter) {
		if(chunk_size(iter) >= size && (!best_chunk || chunk_size(best_chunk) > chunk_size(iter)))
			best_chunk = iter;
		iter=iter->next;
	}
		
	if(!best_chunk) best_chunk=top_chunk;
	else 
		unlink_item(best_chunk);

	return best_chunk;
}

// guaranteed to be called only once
void init_mymalloc() {
	bottom=sbrk(0);
	top=sbrk(MIN_CHUNK_SIZE);

	top_chunk=(chunk*)bottom;
	top_chunk->size=MIN_CHUNK_SIZE;
	top_chunk->next=0;
	top_chunk->prev=0;
	free_list=0;
	//	free_list=top_chunk;	
}

void* mymalloc(int size) {
#ifdef DEBUG
	check_heap();
	check_free_list();
#endif

	int chunksize = sizeof(size_t) + size;
	if(chunksize%8)
		chunksize+=8-(chunksize%8);
	if(chunksize < MIN_CHUNK_SIZE) chunksize = MIN_CHUNK_SIZE;
	char *chunk = 0;

	// see if there is a suitable entry in the list
	chunk = (char*)best_fit(chunksize);

	// special handling for the top - might have to grow the heap!
	if(chunk==(char*)top_chunk) {
		// see if we need to allocate more space, leave room for new top chunk
		if(chunksize+MIN_CHUNK_SIZE > chunk_size(top_chunk)) {
			if(((int)sbrk(chunksize+MIN_CHUNK_SIZE-chunk_size(top_chunk))) == -1) {
				printf("Couldn't allocate %d bytes more memory, failing.\n",size);
				exit(1);
			}		 
		}

		top_chunk=(struct chunk*)(chunk+chunksize);
		top_chunk->size=((char*)sbrk(0)-(char*)top_chunk)+1;
		((struct chunk*)chunk)->size = chunksize | 1; // boundary tag at the beginning. previous chunk must be in use
	}
	else {
		// if there was a free chunk, try to shave off remaining space from the top
		struct chunk* freechunk = (struct chunk*)chunk;
		if(SPLIT_LARGE && (chunk_size(freechunk) > (chunksize + MIN_CHUNK_SIZE))) {

			// initialize a chunk for the leftovers and add it to our free list
			struct chunk* leftover = (struct chunk*)(chunk+chunksize);
			
			// start tag
			leftover->size=chunk_size(freechunk) - chunksize + 1; // +1 since the start of the chunk is now in use
			// end tag
			*(size_t*)(((char*)leftover)+chunk_size(leftover)-sizeof(size_t))=chunk_size(leftover);

			// resize the chunk we're returning. Since it's now in use, it doesn't need an end tag
			freechunk->size=chunksize|1; 
			add_item(leftover);
		}
		else {
			// just inform following chunk that we're now in use
			*((size_t*)(((char*)freechunk)+chunk_size(freechunk))) |= 1;
		}
	}

#ifdef DEBUG
	check_heap();
	check_free_list();
#endif 

	return chunk+sizeof(size_t);
}

void myfree(char* alloc) {
#ifdef DEBUG
	check_heap();
	check_free_list();
#endif 

	chunk* chunk = (struct chunk*)(alloc-sizeof(size_t));	
	// see if we can coalesce this chunk with the previous or following chunk	
	
	// if next is free, increase our size by size of next
	struct chunk *next = (struct chunk*)(alloc-sizeof(size_t)+chunk_size(chunk));

	if(COALESCE && (next==top_chunk || !in_use(next))) {

		if(next!=top_chunk) 
			unlink_item(next);
		else 
			top_chunk=chunk;

		chunk->size += chunk_size(next);
		// update end boundary tag
		*((int*)(((char*)chunk)+chunk_size(chunk)-sizeof(size_t)))=chunk_size(chunk);
	}
	
	// if prev is free, increase its size by our size
	if(COALESCE && ((alloc-sizeof(size_t))>bottom)) {
		
		// if previous chunk is free
		if(!(chunk->size&1)) {
			size_t prevsize = *((size_t*)((char*)alloc-2*sizeof(size_t)));
			struct chunk *prev = (struct chunk*)(alloc-sizeof(size_t)-prevsize);
			unlink_item(prev);
			size_t newsize = prevsize + chunk_size(chunk);
			
			prev->size = newsize + 1;	// what's before had to be in use
			*(size_t*)(((char*)prev)+newsize-sizeof(size_t))=newsize;
			
			if(top_chunk==chunk)
				top_chunk=prev;			
			chunk = prev; // old chunk is now junk!
		}
	}

	// finally, either reduce the top chunk to 16 bytes, or add us to the free list
	if(chunk==top_chunk) {
		sbrk(-chunk_size(top_chunk)+MIN_CHUNK_SIZE);
		top_chunk->size=MIN_CHUNK_SIZE | 1;
		// update end tag
		*((size_t*)(((char*)top_chunk)+MIN_CHUNK_SIZE-sizeof(size_t)))=chunk_size(top_chunk);
	}
	else {		
		*((size_t*)(((char*)chunk)+chunk_size(chunk)-sizeof(size_t)))=chunk_size(chunk);
		*((size_t*)(((char*)chunk)+chunk_size(chunk))) &= ~1; // clear the in-use bit in the next chunk
		add_item(chunk);
	}

#ifdef DEBUG
	check_heap();
	check_free_list();
#endif 
}
