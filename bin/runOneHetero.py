#!/usr/bin/python
# -*- coding: utf-8 -*-

import subprocess
import os.path
import time
import sys
import pandas as pd

work_path = sys.path[0]+'/work_space'
def hetero_to_homo(filepath,jobid,role,garblers):
    """异构图转同构图，并存储"""
    hetero_df = pd.read_csv(filepath,index_col=None,header=None,sep=" ")
    fold_name = os.path.join(work_path,jobid,role,f'input')
    # print(fold_name)
    if  not os.path.exists(fold_name):
        os.makedirs(fold_name)
    edge_and_lenght = {}
    vertex_df = hetero_df[hetero_df.iloc[:,2]==0]
    edge_types = set(hetero_df.iloc[:,2].tolist())-set([0])
    for e in edge_types:
        temp_df = hetero_df[hetero_df.iloc[:,2]==e]
        temp_df = pd.concat([temp_df,vertex_df],axis=0)
        if (temp_df.shape[0])%garblers!=0:
            zero_df = pd.DataFrame([[0,0,0]  for i in range(garblers-temp_df.shape[0]%garblers)])
            temp_df = pd.concat([temp_df,zero_df],axis=0)
        temp_df.to_csv(fold_name+f"/edgeType{e}.csv",index=None,columns=None,header=None,sep=" ")
        edge_and_lenght[e] = temp_df.shape[0]
    return edge_and_lenght



if __name__ == "__main__":
    import click
    @click.command()
    @click.option("--jobid", required=True, type=str, help="jobid")
    @click.option("--experiments", required=True, type=str, help="任务类型")
    @click.option("--role", required=True, type=str, help="角色guest/host")
    @click.option("--input_length", required=True, type=int, help="输入数据长度'")
    @click.option("--garblers", required=False, type=int, default=1, help="进程数，默认单进程")
    @click.option("--num_of_edge_type", required=False, type=int, default=1, help="边类型数目'")
    def start(jobid, experiments,role, input_length,garblers,num_of_edge_type):
        print(experiments.split('.'))
        assert input_length>0
        edge_and_lenght = hetero_to_homo(f"./in/HeteroGraph{input_length}.in",jobid,role,garblers)

        if role == 'guest':
            subprocess.call("./clear_ports.sh", shell=True)
        inputs_length_str = ','.join([str(edge_and_lenght[edge]) for edge in range(1,num_of_edge_type+1)])
        print(inputs_length_str)
        if role == 'guest':
            # subprocess.call("./clear_ports.sh", shell=True)
            params = str(garblers) + " " + inputs_length_str + " " + experiments + " 00 REAL false " +str(num_of_edge_type)+" "+jobid
            print(params)
            print('role guest Alice')
            subprocess.call(["./run_garblers.sh " + params], shell=True)
        elif role=='host':
            params = str(garblers) + " " + inputs_length_str + " " + experiments + " 00 REAL false " +str(num_of_edge_type)+" "+jobid
            print(params)
            print('role host Bob')
            subprocess.call(["./run_evaluators.sh " + params], shell=True)
        if role == 'guest':
            res_df = None
            for experiment in experiments.split(","):
                for EdgeType in range(1,num_of_edge_type+1):
                    task = experiment.split('.')[-1]
                    fold = os.path.join(work_path,jobid,role,f'result/edgeType{EdgeType}',task)
                    for garbid in range(garblers):
                        path = fold+f'{garbid}.csv'
                        try:
                            df_of_garbid = pd.read_csv(path,index_col=None,header=None,sep=" ",engine = "python")
                        except pd.errors.EmptyDataError:
                            df_of_garbid = None
                        # df_of_garbid['EdgeType'] = EdgeType
                        if res_df is None:
                            res_df = df_of_garbid
                        else:
                            if df_of_garbid is not None:
                                res_df = pd.concat([res_df,df_of_garbid],axis=0)
                res_df = res_df.groupby(0).mean().reset_index()
                res_df.to_csv(os.path.join(work_path,jobid,role,f'result/')+task+".csv",index=None,header=None,sep=' ')
                print(res_df)
                        
    start()