
#include <stdlib.h>
#include <unistd.h>
#include <stdio.h>
#include <string.h>
#include <assert.h>
struct memory_region{
    size_t * start;
    size_t * end;
};

struct memory_region global_mem;
struct memory_region heap_mem;
struct memory_region stack_mem;


#define chunk_size(header) (*header & ~(size_t)3)
#define is_marked(header) (*header & (size_t)2)
#define in_use(c) (((size_t*)((char*)c+chunk_size(c))<heap_mem.end)&&(*(size_t*)((char*)c+chunk_size(c))&(size_t)1==1))

void mark(size_t* header){
    *header = *header|2;
}


void unmark(size_t *header){
    *header = *header&~2;
}

size_t* next_chunk(size_t* cur_chunk){
    int size = chunk_size(cur_chunk);
    if((char*)cur_chunk+size<sbrk(0))
        return (size_t*)((char*)cur_chunk+size);
}

size_t* is_pointer(size_t* ptr){
    if(ptr<heap_mem.start||ptr>=(heap_mem.end))
        return 0;
    size_t* chunk = heap_mem.start;
    chunk--;
    while(ptr>(size_t*)((char*)chunk+chunk_size(chunk))){
        chunk = (size_t*)((char*)chunk+chunk_size(chunk));
    }
    if((chunk+1)!=ptr)
        return 0;
    return chunk;
}

//grabbing the address and size of the global memory region from proc 
void init_global_range(){
    char file[100];
    char * line=NULL;
    size_t n=0;
    size_t read_bytes=0;
    size_t start, end;
    
    sprintf(file, "/proc/%d/maps", getpid());
    FILE * mapfile  = fopen(file, "r");
    if (mapfile==NULL){
        perror("opening maps file failed\n");
        exit(-1);
    }
    
    int counter=0;
    while ((read_bytes = getline(&line, &n, mapfile)) != -1) {
        if (strstr(line, "hw3")!=NULL){
            ++counter;
            if (counter==3){
                sscanf(line, "%lx-%lx", &start, &end);
                global_mem.start=(size_t*)start;
                global_mem.end=(size_t*)end;
            }
        }
        else if (read_bytes > 0 && counter==3){
            //if the globals are larger than a page, it seems to create a separate mapping
            sscanf(line, "%lx-%lx", &start, &end);
            if (start==global_mem.end){
                global_mem.end=(size_t*)end;
            }
            break;
        }
    }
    fclose(mapfile);
}

void init_gc() {
    size_t stack_var;
    init_global_range();
    heap_mem.start=malloc(512);
    stack_mem.end = &stack_var;
}

void walk_mark(void *start,void *end){
    for(size_t* start_ptr = (size_t*)start;start_ptr<(size_t*)end;start_ptr++){
        size_t* chunk = is_pointer((size_t*)*start_ptr);
        if(chunk && !is_marked(chunk)){
            mark(chunk);
            walk_mark((void*)(chunk+1),(void*)next_chunk(chunk));
        }
    }

    // for(char* start_ptr = (char*)start;start_ptr<(char*)end;start_ptr++){
    //     size_t* mypointer = (size_t*)start_ptr;
    //     size_t* chunk = is_pointer((size_t*)*mypointer);
    //     if(chunk && !is_marked(chunk)){
    //         mark(chunk);
    //         walk_mark((void*)(chunk+1),(void*)next_chunk(chunk));
    //     }
    // }
}

void sweep(){
    size_t* current = heap_mem.start-1;
    size_t* next = current;
    while(next && next<heap_mem.end){
        current = next;
        if(in_use(current)&&!is_marked(current)){
            next = next_chunk(current);
            if(next && !in_use(next)){
                next = next_chunk(next);
            }
            unmark(current);
            current++;
            free(current);
        }else{
            unmark(current);
            next = next_chunk(current);
        }
    }
    
}

void gc() {
    size_t stack_var;
    heap_mem.end=sbrk(0);
    stack_mem.start = (&stack_var);
    walk_mark((void*)stack_mem.start,(void*)stack_mem.end);
    walk_mark((void*)global_mem.start,(void*)global_mem.end);
    sweep();
}
