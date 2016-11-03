#include<stdio.h>
#include<stdlib.h>
#include<string.h>
#include"hw1.h"

#ifndef MAX_CHUNK
#define MAX_CHUNK 1000
#endif 

void *chunks[MAX_CHUNK];

int another_chunk() {
	for(int i=0;i<MAX_CHUNK;i++) {
		if(!chunks[i]) {
			chunks[i]=malloc(128+((i*137)%12345));
			check_memory();
			return 1;
		}
	}
	return 0;
}

int offset=0;
int oneless_chunk() {
	for(int i=0;i<MAX_CHUNK;i++) {
		offset = random()%MAX_CHUNK;
		if(chunks[offset]) {
			free(chunks[offset]);
			check_memory();
			chunks[offset]=0;
			return 1;
		}
	}
	return 0;
}

int main(int argc, char** argv) {
	init_hw1();
	srandom(1235409);

	memset(chunks,0,sizeof(chunks));
	
	while(another_chunk());	
	while(oneless_chunk());

	for(int i=0;i<MAX_CHUNK;i++)    another_chunk();
	for(int i=0;i<MAX_CHUNK/2;i++)  oneless_chunk();
	for(int i=0;i<MAX_CHUNK/2;i++)  another_chunk();
	for(int i=0;i<MAX_CHUNK/2;i++)  oneless_chunk();
	for(int i=0;i<MAX_CHUNK/2;i++)  another_chunk();
	for(int i=0;i<MAX_CHUNK/2;i++)  oneless_chunk();
	for(int i=0;i<MAX_CHUNK/2;i++)  another_chunk();
	for(int i=0;i<MAX_CHUNK/2;i++)  oneless_chunk();
	for(int i=0;i<MAX_CHUNK/2;i++)  another_chunk();
	for(int i=0;i<MAX_CHUNK;i++)    oneless_chunk();	
}
