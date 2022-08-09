import itertools
import networkx as nx
import json
import matplotlib.pyplot as plt
import pandas as pd
plt.rcParams['font.sans-serif'] = 'SimHei'

def drow_graph(G,attrs_dict={},psi_set=None):
    """可视化图谱"""
    nx.set_node_attributes(G,attrs_dict)
    # generate node_labels manually
    node_labels = {}
    if psi_set is None:
        for node in G.nodes:
            node_labels[node] = G.nodes[node] # G.nodes[node] will return all attributes of node
    else:
         for node in psi_set:
            node_labels[node] = G.nodes[node]

    plt.figure(figsize=(12,12))
    pos = nx.spring_layout(G)
    nx.draw(G, with_labels=True, node_color='skyblue', edge_cmap=plt.cm.Blues, pos = pos)
    nx.draw_networkx_labels(G,pos=pos,labels=node_labels)
    # generate edge_labels manually
    edge_labels = {}
    for edge in G.edges:
        edge_labels[(edge[0],edge[1])] = G[edge[0]][edge[1]][0] # G[edge[0]][edge[1]] will return all attributes of edge
    nx.draw_networkx_edge_labels(G, pos, edge_labels=edge_labels)
    plt.show()

def get_neigbors(g, node, depth=1):
    output = {}
    undirected_g = g.to_undirected()
    layers = dict(nx.bfs_successors(undirected_g, source=node, depth_limit=depth))
    print('sss',layers)
    nodes = [node]
    for i in range(1,depth+1):
        output[i] = []
        for x in nodes:
            output[i].extend(layers.get(x,[]))
        nodes = output[i]
    return output
    
def get_hop_ns(g, psi_set, hop_ns=1):
    # 转为无向图，所有边等同看待
    undirected_g = g.to_undirected()
    nodes_it=itertools.chain()
    for node in psi_set:
        #深度遍历每个节点的hop_ns领域
        layers = nx.bfs_successors(undirected_g, source=node, depth_limit=hop_ns)
        nodes_it = itertools.chain(nodes_it,layers)
    hop_ns = []
    hop_ns.extend(psi_set)
    for nodes in nodes_it:
        hop_ns.extend(nodes[1]) 
    return set(hop_ns)

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
    # G.add_edge('1111','国鸿氢能', name='www', weight=20)    
    psi_set = ['国鸿氢能','技防系统','中移动']
    hop_ns_set = get_hop_ns(G, psi_set, hop_ns=1)
    subgraph = G.subgraph(hop_ns_set).copy()
    ################## 匿名化##############
    node_encode_dict = {}
    #交集内从0开始
    node_encode_dict.update(dict(zip(psi_set,range(len(psi_set)))))
    #交集外从len(psi_set)开始
    node_encode_dict.update(dict(zip(hop_ns_set-set(psi_set),range(len(psi_set),len(hop_ns_set)))))
    subgraph = nx.relabel_nodes(subgraph,node_encode_dict)
    drow_graph(subgraph,attrs_dict={})

    #### 恢复图
    revert_dict = dict(zip(node_encode_dict.values(),node_encode_dict.keys()))
    subgraph = nx.relabel_nodes(subgraph,revert_dict)
    drow_graph(subgraph,attrs_dict={})