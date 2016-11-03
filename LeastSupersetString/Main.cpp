#include <iostream>
#include <stdio.h>
#include <string>
#include <list>
#include <set>
#include <vector>
using namespace std;

// Trim function taken help from stackoverflow
string trim(string& str)
{
    size_t first = str.find_first_not_of(' ');
    size_t last = str.find_last_not_of(' ');
    return str.substr(first, (last-first+1));
}

int* prefixPreprocessing(string a){
	int i = 1,j=0;
	int* prefix = new int[a.size()];
	prefix[0] = 0;
	while(i<a.size()){
		if(a[i]==a[j]){
			prefix[i] = j+1;
			i++;j++;
		}else{
			while(a[i]!=a[j] && j>0){
				j = prefix[j-1];
			}
			prefix[i] = j;
			i++;
		}
	}
	return prefix;
}

string getCommonSubstring(string a,string b){
	int maxCommon = 0;
	//int* prefixForB = prefixPreprocessing(b);
	int j = 0;
	for(int i=0;i<a.size() && maxCommon==0;i++){
		for(int j=0;j<b.size() && (i+j)<a.size();j++){
			if(a[i+j] == b[j]){
				maxCommon++;
			}else{
				maxCommon = 0;
				break;			
			}
		}		
	}
	/*int j = 0;
	for(int i=0;i<a.size() && maxCommon==0;i++){		
		if(a[i+j] == b[j]){
			maxCommon++;
		}else{
			j=prefixForB[j];
			if(j!=0)
				i--;
			maxCommon = j;
			break;			
		}				
	}*/
	return b.substr(0,maxCommon);
}

int getMaxInMatrix(int **matrix,int size,set<int> blackedOutRow,set<int> blackedOutColumn,int &a,int &b){
	int max = 0;
	for(int i=0;i<size;i++){
		for(int j=0;j<size;j++){
			if(blackedOutRow.find(i)!=blackedOutRow.end() && blackedOutColumn.find(j)!=blackedOutColumn.end() && matrix[i][j]>max){
				if(!(blackedOutRow.find(j)!=blackedOutRow.end() && blackedOutColumn.find(i)!=blackedOutColumn.end()) ){
					a = i;
					b = j;
					max = matrix[i][j];
				}				
			}
		}
	}
	return max;
}

void getLeastSupersetString(string* sequences,int noOfSequence){
	int **matrix = new int*[noOfSequence];
	for(int i=0;i<noOfSequence;i++){
		matrix[i] = new int[noOfSequence]();
	}
	for(int i=0;i<noOfSequence;i++){
		for(int j=0;j<noOfSequence;j++){
			if(i!=j){
				matrix[i][j] = getCommonSubstring(sequences[i],sequences[j]).size();
			}else{
				matrix[i][j] = -1;
			}
		}
	}
	vector<list<int>> connectedNodes;
	int noOfList = 0;
	int keepChecking = noOfSequence-1;
	set<int> blackedOutRow;set<int> blackedOutColumn;
	while(keepChecking>0){
		int a =0,b=0;
		int max = -1;
		for(int i=0;i<noOfSequence;i++){
			for(int j=0;j<noOfSequence;j++){
				if(blackedOutRow.find(i)==blackedOutRow.end() && blackedOutColumn.find(j)==blackedOutColumn.end() && matrix[i][j]>max){
					if(	blackedOutColumn.find(i)!=blackedOutColumn.end()){
						for(vector<list<int>>::iterator it = connectedNodes.begin();it!=connectedNodes.end();it++){
							if(it->back()==i && it->front()!=j){
								max = matrix[i][j];
								a = i;
								b=j;
								break;
							}
						}
					}else{
						max = matrix[i][j];
						a = i;
						b=j;
					}
				}
			}
		}
		if(blackedOutRow.find(b)!=blackedOutRow.end() && blackedOutColumn.find(a)!=blackedOutColumn.end()){
			for(vector<list<int>>::iterator it = connectedNodes.begin();it!=connectedNodes.end();it++){
				if(it->back()==a){
					for(vector<list<int>>::iterator it1 = connectedNodes.begin();it1!=connectedNodes.end();it1++){
						if(it1->front()==b){
							it->insert(it->end(),it1->begin(),it1->end());
							connectedNodes.erase(it1);
							break;
						}
					}
					break;
				}
			}
			blackedOutRow.insert(a);
			blackedOutColumn.insert(b);
		}else if(blackedOutColumn.find(a)!=blackedOutColumn.end()){
			for(vector<list<int>>::iterator it = connectedNodes.begin();it!=connectedNodes.end();it++){
				if(it->back()==a){
					it->push_back(b);
					break;
				}
			}
			blackedOutColumn.insert(b);
			blackedOutRow.insert(a);
		}else if(blackedOutRow.find(b)!=blackedOutRow.end()){
			for(vector<list<int>>::iterator it = connectedNodes.begin();it!=connectedNodes.end();it++){
				if(it->front()==b){
					it->push_front(a);
					break;
				}
			}
			blackedOutRow.insert(a);
			blackedOutColumn.insert(b);
		}else{
			list<int> newList;
			newList.push_back(a);
			newList.push_back(b);
			connectedNodes.push_back(newList);
			blackedOutColumn.insert(b);
			blackedOutRow.insert(a);
		}
		keepChecking--;
	}
	list<int> result = connectedNodes.front();
	int previous = -1;
	int spaces = 0;
	int finalLength = 0;
	cout<<endl;
	for(list<int>::iterator it = result.begin();it!=result.end();it++){
		if(previous!=-1){
			string common = getCommonSubstring(sequences[previous],sequences[*it]);
			spaces+=(sequences[previous].size()-common.size());
			for(int s =0;s<spaces;s++){
				cout<<" ";
			}
			cout<<sequences[*it]<<endl;
			finalLength = spaces+sequences[*it].size();
		}else{
			cout<<sequences[*it]<<endl;
			finalLength = sequences[*it].size();
		}
		previous = *it;
	}
	cout<<"The length is : "<<finalLength<<endl;
}

int main(){
	/*string a = "1001002";
	string b = "1002110";
	string common = getCommonSubstring(a,b);
	cout<<"Length is "<<common.size()<<endl;
	cout<<common<<endl;
	int x;
	cin>>x;*/
	int noOfSequences = 0;
	cin>>noOfSequences>>ws;
	string* sequences = new string[noOfSequences];
	for(int i=0;i<noOfSequences;i++){
		getline(cin,sequences[i]);
		sequences[i] = trim(sequences[i]);
	}
	getLeastSupersetString(sequences,noOfSequences);
	int x;
	cin>>x;
	return 0;
}