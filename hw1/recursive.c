#include<stdio.h>
#include<stdlib.h>
#include<string.h>
#include"hw1.h"

#ifndef MAX_CHUNK
#define MAX_CHUNK 1000
#endif 

void *chunks[MAX_CHUNK];

int allocate_chunks(int count) {
	for(int i=0;i<MAX_CHUNK;i++) {
		if(!chunks[i]) {
			chunks[i]=malloc(128+((i*137)%12345));
			check_memory();

			if(count==1)
				return 1;
			else
				return allocate_chunks(count-1);
		}
	}
	return 0;
}

int free_chunks(int count) {
	for(int i=0;i<MAX_CHUNK;i++) {
		int index = i*37%MAX_CHUNK;
		if(chunks[index]) {
			free(chunks[index]);
			check_memory();

			chunks[index]=0;
			if(count==1)
				return 1;
			else
				return free_chunks(count-1);
		}
	}
	return 0;
}

int main(int argc, char** argv) {
	init_hw1();

	memset(chunks,0,sizeof(chunks));
	
	if(!allocate_chunks(MAX_CHUNK))   printf("Couldn't allocate 1.\n");
	if(!free_chunks(MAX_CHUNK))       printf("Couldn't free 1.\n");

	if(!allocate_chunks(MAX_CHUNK)) printf("Couldn't allocate 2.\n");
	if(!free_chunks(MAX_CHUNK/2))     printf("Couldn't free 2.\n");
	if(!allocate_chunks(MAX_CHUNK/2)) printf("Couldn't allocate 3.\n");
	if(!free_chunks(MAX_CHUNK/2))     printf("Couldn't free 3.\n");
	if(!allocate_chunks(MAX_CHUNK/2)) printf("Couldn't allocate 4.\n");
	if(!free_chunks(MAX_CHUNK/2))     printf("Couldn't free 4.\n");
	if(!allocate_chunks(MAX_CHUNK/2)) printf("Couldn't allocate 5.\n");
	if(!free_chunks(MAX_CHUNK/2))     printf("Couldn't free 5.\n");
	if(!allocate_chunks(MAX_CHUNK/2)) printf("Couldn't allocate 6.\n");
	if(!free_chunks(MAX_CHUNK))       printf("Couldn't free 6.\n");
}
