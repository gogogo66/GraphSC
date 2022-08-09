from functools import reduce
import networkx as nx
import json
import matplotlib.pyplot as plt
import pandas as pd
from functools import reduce

def multi2graph(G:nx.multigraph):
    gs = {}
    for u,v,data in G.edges(data=True):
        # print(data)
        edge = data['edge'] 
        if edge  not in gs.keys():
            gs[edge] = nx.Graph()
        gs[edge].add_edge(u, v)
    return gs

def dict_sum(d1, d2):
    keys = d1.keys() | d2.keys()
    value_dic = {}
    for key in keys:
        value_dic[key] = sum([d.get(key,0) for d in (d1, d2)])
    return value_dic
def dict_union(*ds):
    def union_func(d1,d2):
        return d1 | d2
    keys = reduce(union_func,[d.keys() for d in ds])
    # print('ss',keys)
    value_dic = {}
    for key in keys:
        value_dic[key] = [d.get(key,0) for d in ds]
    return value_dic
if __name__=='__main__':
    ######读取数据集，可换为其他方式############
    path = "D:/insight_work/fL/graph/seedKG/"
    with open(path+"relationships.json",encoding='utf8') as f:
        relationships = json.load(f)
    with open(path+"attributes.json",encoding='utf8') as f:
        attributes = json.load(f)
    relations = [ere[1] for ere in relationships['relationships']]
    source = [ere[0] for ere in relationships['relationships']]
    target = [ere[2] for ere in relationships['relationships']]
    kg_df = pd.DataFrame({'source':source, 'target':target, 'edge':relations})
    kg_df.edge.value_counts(),len(kg_df.source.unique())
    #########构建图，并找到交集的n阶领域子图######
    G=nx.from_pandas_edgelist(kg_df, "source", "target", edge_attr=True, create_using=nx.MultiDiGraph())  
    gs = multi2graph(G)
    betweenness_centralitys = reduce(dict_sum, [nx.betweenness_centrality(g) for g in gs.values()])
        
    res = pd.DataFrame(dict_union(*[dict(G.in_degree),dict(G.out_degree),nx.pagerank(G),betweenness_centralitys],nx.closeness_centrality(G)),\
        index=['in_degree','out_degree','pagerank','betweenness_centralitys','closeness_centrality']).T
    # res.columns = ['in_degree','out_degree','pr','bc']
    print(res.head())