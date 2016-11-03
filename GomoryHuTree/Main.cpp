#include <iostream>
#include <stdio.h>
#include <limits.h>
#include <string>
#include <queue>
#include <stack>
#include <set>
using namespace std;

short int noOfVertex;
short int noOfEdges;
bool bfs(short int** graph,short int source,short int destination,short int* path){
	bool* visited = new bool[noOfVertex]();
	queue<short int> q;
	q.push(source);
	visited[source] = true;
	path[source]=-1;
	while (!q.empty())
	{
		short int currentNode = q.front();
		q.pop();
		for(short int i=0;i<noOfVertex;i++){
			if(visited[i]==false && graph[currentNode][i]>0){
				q.push(i);
				path[i] = currentNode;
				visited[i] = true;
			}
		}
	}
	bool hasVisited = visited[destination];
	delete[] visited;
	return hasVisited;
}

void dfs(short int** graph, short int s, bool* visited){
	visited[s] = true;
	//cout<<"visiting "<<s<<endl;
	for (short int i = 0; i < noOfVertex; i++)
       if (graph[s][i] && !visited[i])
           dfs(graph, i, visited);
}

short int minCut(short int** graph, short int s, short int t,set<short int> & x)
{
    short int u, v;
 
    // Create a residual graph and fill the residual graph with
    // given capacities in the original graph as residual capacities
    // in residual graph
	short int** rGraph = new short int*[noOfVertex]; // rGraph[i][j] indicates residual capacity of edge i-j
	for(short int i =0;i<noOfVertex;i++){
		rGraph[i] = new short int[noOfVertex]();
	}
    for (u = 0; u < noOfVertex; u++)
        for (v = 0; v < noOfVertex; v++)
             rGraph[u][v] = graph[u][v];
 
    short int* parent = new short int[noOfVertex]();  // This array is filled by BFS and to store path
	short int maxFlow = 0;
    // Augment the flow while tere is path from source to sink
    while (bfs(rGraph, s, t, parent))
    {
        // Find minimum residual capacity of the edhes along the
        // path filled by BFS. Or we can say find the maximum flow
        // through the path found.
        short int path_flow = 30000;
        for (v=t; v!=s; v=parent[v])
        {
            u = parent[v];
            path_flow = min(path_flow, rGraph[u][v]);
        }
 
        // update residual capacities of the edges and reverse edges
        // along the path
        for (v=t; v != s; v=parent[v])
        {
            u = parent[v];
            rGraph[u][v] -= path_flow;
            rGraph[v][u] += path_flow;
        }
		maxFlow+=path_flow;
    }
	
    // Flow is maximum now, find vertices reachable from s
    bool* visited = new bool[noOfVertex]();
    dfs(rGraph, s, visited);
    // Prshort int all edges that are from a reachable vertex to
    // non-reachable vertex in the original graph
 //   for (short int i = 0; i < noOfVertex; i++){
 //     for (short int j = 0; j < noOfVertex; j++){
 //        if (visited[i] && !visited[j] && graph[i][j]){
	//		 x.erase(j);
	//		 //cout<<"not reachable "<<j<<endl;
	//	 }else{
	//		 /*x.insert(j);*/
	//	 }
	//  }
	//}
	for(short int i=0;i<noOfVertex;i++){
		if(!visited[i]){
			x.erase(i);
		}
	}
	for(int i=0;i<noOfVertex;i++){
		delete[] rGraph[i];
		rGraph[i] = NULL;
	}
	delete[] rGraph;
	rGraph = NULL;
	delete[] visited;
	visited = NULL;
	delete[] parent;
	parent = NULL;
	return maxFlow;
}

void Gusfield(short int** graph){
	short int* p = new short int[noOfVertex]();
	short int* fl = new short int[noOfVertex]();
	for(short int source = 1;source<noOfVertex;source++){
		cout<<"Source : "<<source<<endl;
		short int sink = p[source];
		set<short int>x;
		for(short int i=0;i<noOfVertex;i++){
			x.insert(i);
		}
		short int maxFlow = minCut(graph,source,sink,x);
		fl[source] = maxFlow;
		for(short int i=0;i<noOfVertex;i++){
			if(i!=source && (x.find(i)!=x.end()) && p[i] ==sink){
				p[i] = source;
				//cout<<"p["<<i<<"] = "<<source<<"\n";
			}
		}
		if((x.find(p[sink])!=x.end())){
			p[source]=p[sink];
			//cout<<"p["<<source<<"] = "<<sink<<"\n";
			p[sink] = source;
			//cout<<"p["<<sink<<"] = "<<source<<"\n";
			fl[source] = fl[sink];
			//cout<<"fl["<<source<<"] = "<<source<<"fl["<<sink<<"] = "<<sink<<"\n";
			fl[sink] = maxFlow;
			//cout<<"fl["<<sink<<"] = "<<maxFlow<<"\n";
		}
		//prshort intf("s:%d , t:%d, maxFlow:%d\n", source,sink,maxFlow);
		/*for(short int k = 1;k<noOfVertex;k++){
			cout<<k<<" "<<p[k]<<" "<<fl[k]<<"\n";
		}
		cout<<endl;*/
	}
	for(short int k = 1;k<noOfVertex;k++){
		cout<<k<<" "<<p[k]<<" "<<fl[k]<<"\n";
	}
}

short int main(){
	short int m,s,t,cost, maxFlow;
    short int** graph; 
    short int *p, *f1;
    
    
    //prshort intf("Enter no of vertices: ");
	cin>>noOfVertex;
    //prshort intf("Enter no of edges: ");
	cin>>noOfEdges;
	//noOfVertex = 15;noOfEdges=24;
     
    graph = new short int *[noOfVertex];
	/*short int p1[] = {0,0,1,1,2,2,3,3,4,5,5,5,6,7,7,8,8,9,9,10,11,12,13,13}; 
	short int p2[] = {1,2,3,4,5,6,4,7,7,4,6,9,10,8,11,4,11,6,11,11,12,13,11,14}; 
	short int p3[] = {20,20,12,8,15,5,4,8,18,2,10,3,17,11,15,4,7,2,1,17,43,43,3,40};*/
    for (short int i=0 ; i < noOfVertex ; i++){        
        graph[i] = new short int[noOfVertex]();
    }
   
    
	for(short int i =0; i < noOfEdges ; i++){
        
        cin>>s;
        cin>>t;
        cin>>cost;
        graph[s][t] = cost;
        graph[t][s] = cost;
        /*graph[p1[i]][p2[i]] = p3[i];
        graph[p2[i]][p1[i]] = p3[i];*/
    }
	cout<<endl;
	Gusfield(graph);
	string a ;
	cin >> a;
}