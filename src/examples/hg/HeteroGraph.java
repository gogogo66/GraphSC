package examples.hg;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;

import javax.lang.model.util.ElementScanner6;

import parallel.Gadget;
import parallel.GraphNode;
import parallel.Machine;
import util.Constants;
import util.Utils;
import flexsc.CompEnv;
import flexsc.Party;
import gc.GCSignal;

import examples.pr.PageRankNode;
import examples.pr.PageRank;

public class HeteroGraph<T> extends Gadget<T> {
	static int ITERATIONS = 1;

	public HeteroGraph(CompEnv<T> env, Machine machine) {
		super(env, machine);
	}

	private void printVertex(T isV)
	{
		boolean temp = env.outputToAlice(isV);
		env.channel.flush();
		System.out.println(temp+ " "+((GCSignal) isV).toHexStr());
	}

	private void printNode(PageRankNode<T>[] nodes,int idx,int garbid){
		int v_num=0;
        if(machine.getGarblerId()==garbid){
		for (int i=idx; i<nodes.length; i++){
			if (i == -1) {
				++i;
			}
			int u = Utils.toInt(env.outputToAlice(nodes[i].u));
			int v =  Utils.toInt(env.outputToAlice(nodes[i].v));
			// int e =  Utils.toInt(env.outputToAlice(nodes[i].EdgeType));
            int e = 1;
			boolean isv = env.outputToAlice(nodes[i].isVertex);
			if (isv){v_num++;}
			env.channel.flush();
			if (Party.Alice.equals(env.party)){
				System.out.println(u+" "+v+" "+ e +" " +isv+" "+((GCSignal) nodes[i].isVertex).toHexStr());
			}
			if (idx != -1)
				break;
		}
		if (idx == -1)
		    System.out.println("v_num"+v_num);
    }
	}

    private boolean isPowerOfTwo(int num)
	{
		return num>0&&((num-1)&num)==0;
	}

    private void writeResult(int garblerId,String jobid,HashMap<Integer,Double> resDict) throws IOException {

        String dir = System.getProperty("user.dir") +"/result/"+jobid;
        //文件夹
        System.out.format(dir);
        File file = new File(dir);
        if (!file.exists()) 
            file.mkdirs();
        
        // 文件
        file = new File(dir+"/PageRank"+garblerId+".csv");
        if (!file.exists()) 
            file.createNewFile();
        FileWriter writer = new FileWriter(file);
        for(int u:resDict.keySet()) {
            double pageRank = resDict.get(u);
            writer.write(String.format("%d %.2f\n", u, pageRank));
		}
        writer.flush();
        writer.close();
	}

	private Map<Integer,List<Object[]>> getInput(int inputLength, int garblerId, int processors, String inputPath
	) throws IOException {
		if(inputPath==null){
			inputPath = Constants.INPUT_DIR + "HeteroGraph" + inputLength * processors + ".in";
		}
		BufferedReader br = new BufferedReader(new FileReader(inputPath));
		int[] u = new int[inputLength];
		int[] v = new int[inputLength];
		int[] edgeType  = new int[inputLength]; //边的类型，为0时表示是顶点
		boolean[][][] vertexFea = new boolean[inputLength][][];
        
		int j = 0;
		for (int i = 0; i < inputLength * processors; i++) {
			String readLine = br.readLine();
			if (!(i >= garblerId * inputLength && i < (garblerId + 1) * inputLength)) {
				continue;
			}
			String[] split = readLine.split(" ");
			u[j] = Integer.parseInt(split[0]);
			v[j] = Integer.parseInt(split[1]);
			edgeType[j] = Integer.parseInt(split[2]);
			if (split.length>3){
				//读取顶点上的特征
				String[] feaStr = split[3].split(",");
				for(int k=0; k<feaStr.length; k++){
					vertexFea[j][k] = Utils.fromFixPoint(Double.parseDouble(feaStr[3]), HeteroGraphNode.WIDTH, HeteroGraphNode.OFFSET);
				}
			}
			j++;

		}
		br.close();

        Map<Integer,List<Object[]>> nodeDict = new HashMap<Integer,List<Object[]>>();
		// boolean[][] a = new boolean[u.length][];
		// boolean[][] b = new boolean[v.length][];
		// boolean[][] c = new boolean[edgeType.length][];

        
		for(int i = 0; i < u.length; ++i) {
            Object[] ret = new Object[4];
			ret[0] = u[i];
			ret[1] = v[i];
			ret[2] = edgeType[i];
            ret[3] = vertexFea[i];
            if (nodeDict.get(edgeType[i])==null){
                ArrayList<Object[]> tempList = new ArrayList<Object[]>();
                tempList.add(ret);
                nodeDict.put(edgeType[i],tempList);
            }
            else{
                nodeDict.get(edgeType[i]).add(ret);
            }
		}
        for(int i=0;i<machine.getNumOfedgeType()+1;i++){
            List<Object[]> input = (List<Object[]>) nodeDict.get(i);
            if(input!=null){
                for(int k=0;k<input.size();k++){
                    System.out.println("garbid"+garblerId+" key " +i+input.get(k)[0]+
                                                        " "+input.get(k)[1]);
                }
            }else{
                System.out.println("garbid"+garblerId+" no value of key "+i+" in this process");
            }
        }
		return nodeDict;
	}

    @Override
	public Object secureCompute() throws Exception {
		String[] inputLengthes =machine.getInputLength().split(",");
        int inputLength = Integer.parseInt(inputLengthes[0])/ machine.getTotalMachines();
        System.out.println(inputLength);
        int numOfedgeType = machine.getNumOfedgeType();
        Map<Integer,List<Object[]>> inputDict = getInput(inputLength, machine.getGarblerId(),
                                                        machine.getTotalMachines(),null); //Gen Alice生成器方数据
        //获取图顶点
        int numOfNode=0;
        if(inputDict.get(0)!=null)
            numOfNode=inputDict.get(0).size();
        List<Integer> allVertexList = new ArrayList<>(); 
        for(int j=0;j<numOfNode;j++){
            int temp = (int) inputDict.get(0).get(j)[0];
            allVertexList.add(temp);
            System.out.println("rawvertex: "+j+" "+temp);
        }
        //分边的类型计算
        for(int i=1;i<numOfedgeType+1;i++){
            List<Object[]> input = (List<Object[]>) inputDict.get(i);
            int numOfEadge;
            if(input==null) {
                numOfEadge= 0;
                input = new ArrayList<Object[]>();
            }
            else{
                numOfEadge=input.size();
            }
            System.out.println("------numOfEadge: "+numOfEadge);

            
            // boolean[][] uSelf = new boolean[numOfNode][GraphNode.VERTEX_LEN];
            // boolean[][] vSelf= new boolean[numOfNode][GraphNode.VERTEX_LEN];
            // boolean[] isVSelf = new boolean[numOfNode];
            List<Integer> uList=new ArrayList<>();
            List<Integer> vList=new ArrayList<>();
            // List<Integer> uvList=new ArrayList<>();
            
            for(int j=0;j<numOfEadge;j++){
                uList.add((int) input.get(j)[0]);
                vList.add((int) input.get(j)[1]);
                // System.out.println(Utils.toInt(uList.get(j))+" "+Utils.toInt(vList.get(j))+" ");
            } 
            // //获取顶点信息，即已有边去重
            // uvList.addAll(uList);
            // System.out.println("uvList: "+uvList.size());
            // uvList.addAll(vList);
            // System.out.println("uvList: "+uvList.size());
            // List<Integer> vertexList =uvList.stream().distinct().
            //                                  collect(java.util.stream.Collectors.toList());
            // for(int j=0;j<vertexList.size();j++){
            //     System.out.println(vertexList.get(j)+" kkkkkkkkkkkkkkkkk "+vertexList.get(j));
            // } 
            // System.out.println("vertexList: "+vertexList.size());
            // allVertexList.retainAll(vertexList);
            // System.out.println("allVertexList: "+allVertexList.size());
            uList.addAll(allVertexList);
            vList.addAll(allVertexList);

            //发送本方样本数量
            int tempNumOfNode = uList.size();
            env.channel.writeInt(tempNumOfNode);
            env.channel.flush();
            int otherNumOfNode = env.channel.readInt();
            System.out.println("tempNumOfNode: "+tempNumOfNode+"-----otherNumOfNode: "+otherNumOfNode);
            //如果数据不满足2的指数幂，要扩充(0,0),并且表示为边即结果不保存
            if((!isPowerOfTwo(tempNumOfNode+otherNumOfNode))){
                System.out.println("covert input lenth to power of two");
                int binaryLen =Integer.toBinaryString(tempNumOfNode+otherNumOfNode).length()-1;
                if(Party.Alice.equals(env.party)){
                    numOfNode = (2<<binaryLen)-otherNumOfNode;
                    for(int j=0;j<numOfNode-tempNumOfNode;j++){
                        //补充0 0点
                        uList.add(0);
                        vList.add(0);
                    }
                }
                else{
                    numOfNode = tempNumOfNode;
                    otherNumOfNode = (2<<binaryLen)-numOfNode;
                }
            }
            else{
                numOfNode = tempNumOfNode;
            }
            System.out.println("NumOfNode: "+numOfNode+"-----otherNumOfNode: "+otherNumOfNode);

            boolean[][] uSelf = new boolean[numOfNode][GraphNode.VERTEX_LEN];
            boolean[][] vSelf= new boolean[numOfNode][GraphNode.VERTEX_LEN];
            boolean[] isVSelf = new boolean[numOfNode];
            for(int j=0;j<numOfNode;j++){
                System.out.println(uList.get(j)+" "+vList.get(j)+" ");
                uSelf[j] = Utils.fromInt(uList.get(j), GraphNode.VERTEX_LEN) ;
                vSelf[j] = Utils.fromInt(vList.get(j), GraphNode.VERTEX_LEN) ;
                if(j<numOfEadge)
                    isVSelf[j]=false;
                else if(j<tempNumOfNode)
                    isVSelf[j]=true;
                else 
                    isVSelf[j]=true;
            }
             
            boolean[][] uOther = new boolean[otherNumOfNode][GraphNode.VERTEX_LEN];
            boolean[][] vOther= new boolean[otherNumOfNode][GraphNode.VERTEX_LEN];
            boolean[] isVOther = new boolean[otherNumOfNode];

            T[][] tu_a=null;
            T[][] tv_a=null;
            T[]   isV_a=null;
            T[][] tu_b=null;
            T[][] tv_b=null;
            T[]  isV_b=null;
            if (env.getParty().equals(Party.Alice)){
                tu_a = (T[][]) env.inputOfAlice(uSelf);
                tv_a = (T[][]) env.inputOfAlice(vSelf);
                isV_a = (T[]) env.inputOfAlice(isVSelf);
                tu_b = (T[][]) env.inputOfBob(uOther);
                tv_b = (T[][]) env.inputOfBob(vOther);
                isV_b = (T[]) env.inputOfBob(isVOther);
            }else{
                tu_a = (T[][]) env.inputOfAlice(uOther);
                tv_a = (T[][]) env.inputOfAlice(vOther);
                isV_a = (T[]) env.inputOfAlice(isVOther);
                tu_b = (T[][]) env.inputOfBob(uSelf);
                tv_b = (T[][]) env.inputOfBob(vSelf);
                isV_b = (T[]) env.inputOfBob(isVSelf);
            }
            PageRankNode<T>[] nodes = (PageRankNode<T>[]) Array.newInstance(PageRankNode.class, numOfNode+otherNumOfNode);
            int AliceNodesNum = tu_a.length;
            for (int k = 0; k < nodes.length; k++) {
                if(k<AliceNodesNum){
                    nodes[k] = new PageRankNode<T> (tu_a[k], tv_a[k], isV_a[k],env);
                }else{
                    nodes[k] = new PageRankNode<T> (tu_b[k-AliceNodesNum], tv_b[k-AliceNodesNum], isV_b[k-AliceNodesNum],env);
                }
                printNode(nodes, k,0);
            }
            HashMap<Integer,Double> resDict = (new PageRank(env,machine)).secureCompute(nodes);
            if(env.party.equals(Party.Alice)){
                writeResult( machine.getGarblerId(), machine.getJobid()+"/edgeType"+i,resDict);
            }
        }
        return null;
    }

}