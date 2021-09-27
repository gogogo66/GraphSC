GraphSC
======

GraphSC is a parallel secure computation framework that supports graph-parallel programming abstractions resembling GraphLab. GraphSC is suitable for both multi-core and cluster-based computing architectures. Link for the [paper](http://www.cs.umd.edu/~kartik/papers/3_graphsc.pdf).

## Installing GraphSC
git clone https://github.com/kartik1507/GraphSC.git

## Compiling and Running GraphSC - Basic Usage
cd GraphSC/bin/

./compile.sh

./runOne.py <experiment> <inputlength> <garblers>

e.g. ./runOne.py pr.PageRank 16 2

The above example will run the PageRank example using 2 garblers and 2 evaluators on the same machine. The configuration for running garblers and evaluators on a cluster can be found in machine_spec/*. The input files are stored in in/*.

In case of any queries, please contact Kartik Nayak (kartik@cs.umd.edu)


## 新增by hym
1.支持异构图

2.异构图的输入格式需按照./in/HeteroGraph.in，即0表示顶点，其他数字表示边的类型（与其他示例中1表示顶点不同）

3.目前只改造了PageRank

4.支持两方都有数据

5.支持Alice(即guest)获取结果，结果存储在./work_space/jobid/role/result下（运行文件自动生成）

6.待支持多种特征的提取--experiments pr.PageRank,histogram.Histogram

## 启动命令：
（本地单机测试时，需先启动guest，因为他会先杀死相关进程）

python ./runOneHetero.py --jobid test --experiments pr.PageRank --role guest --input_length 10 --garblers 4 --num_of_edge_type 2

python ./runOneHetero.py --jobid test --experiments pr.PageRank --role host --input_length 20 --garblers 4 --num_of_edge_type 2