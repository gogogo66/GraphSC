package examples;

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
import examples.histogram.Histogram;
import examples.histogram.HistogramNode;

public class HeteroGraph<T> extends Gadget<T> {
	static int ITERATIONS = 1;
    FileWriter logger;
    String role;

	public HeteroGraph(CompEnv<T> env, Machine machine) {
		super(env, machine);
        try {
            this.logger = initLogStream(machine.getJobid(),machine.getGarblerId());
            if(env.party.equals(Party.Alice)){
                this.role="guest";
            }
            else{
                this.role="host";
            }
        } catch (Exception e) {
            this.logger=null;
        }
       
	}

	private void printVertex(T isV) throws IOException
	{
		boolean temp = env.outputToAlice(isV);
		env.channel.flush();
		log(temp+ " "+((GCSignal) isV).toHexStr());
	}

	private void printNode(GraphNode<T>[] nodes,int idx,int edgeType) throws IOException{
		int v_num=0;
		for (int i=idx; i<nodes.length; i++){
			if (i == -1) {
				++i;
			}
			int u = Utils.toInt(env.outputToAlice(nodes[i].u));
			int v =  Utils.toInt(env.outputToAlice(nodes[i].v));
			// int e =  Utils.toInt(env.outputToAlice(nodes[i].EdgeType));
			boolean isv = env.outputToAlice(nodes[i].isVertex);
			if (isv){v_num++;}
			env.channel.flush();
			if (Party.Alice.equals(env.party)){
				log(u+" "+v+" "+ edgeType +" " +isv+" "+((GCSignal) nodes[i].isVertex).toHexStr());
			}
			if (idx != -1)
				break;
		}
		if (idx == -1)
		    log("v_num"+v_num);
    }

    private boolean isPowerOfTwo(int num)
	{
		return num>0&&((num-1)&num)==0;
	}

    private FileWriter initLogStream(String jobid,int garbid) throws IOException {
        String role;
        if (Party.Alice.equals(env.party)){
            role = "guest";
        }
        else{
            role ="host";
        }

        String dir = System.getProperty("user.dir") +"/work_space/"+jobid+"/"+role;
        //文件夹
        // System.out.format(dir);
        File file = new File(dir);
        if (!file.exists()) 
            file.mkdirs();
        
        // 文件
        file = new File(dir+"/"+"garbid"+garbid+".log");
        if (!file.exists()) 
            file.createNewFile();
        FileWriter writer = new FileWriter(file);
        return writer;
	}

    private void log(String mes) throws IOException{
        if(this.logger==null){
            System.out.println(mes);
        }
        else{
            this.logger.write(mes+"\n");
            this.logger.flush();
        }
    }

    private void writeResult(String fileName,String jobid,int edgeType,HashMap<Integer,Double> resDict) throws IOException {

        String dir = System.getProperty("user.dir") +"/work_space/"+jobid+"/"+role+"/result/edgeType"+edgeType;
        //文件夹
        // System.out.format(dir);
        File file = new File(dir);
        if (!file.exists()) 
            file.mkdirs();
        
        // 文件
        file = new File(dir+"/"+fileName+".csv");
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
            if(readLine==null){
                throw new AssertionError("getinput readLine is null,please check inputSize !!!");
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
                    log("garbid"+garblerId+" key "+ i +" "+input.get(k)[0]+
                                                        " "+input.get(k)[1]);
                }
            }else{
                log("garbid"+garblerId+" no value of key "+i+" in this process");
            }
        }
		return nodeDict;
	}

    private List<Object[]> getInput(int inputLength, int garblerId, int processors,int edgeType ) throws 
    IOException {
        String inputPath = "./work_space/" +machine.getJobid()+ "/"+role+"/input/edgeType" + edgeType + ".csv";
		BufferedReader br = new BufferedReader(new FileReader(inputPath));
		int[] u = new int[inputLength];
		int[] v = new int[inputLength];
		boolean[] isVertex  = new boolean[inputLength]; //边的类型，为0时表示是顶点
		boolean[][][] vertexFea = new boolean[inputLength][][];
        
		int j = 0;
		for (int i = 0; i < inputLength * processors; i++) {
			String readLine = br.readLine();
			if (!(i >= garblerId * inputLength && i < (garblerId + 1) * inputLength)) {
				continue;
			}
            if(readLine==null){
                throw new AssertionError("getinput readLine is null,please check inputSize !!!");
            }
			String[] split = readLine.split(" ");
			u[j] = Integer.parseInt(split[0]);
			v[j] = Integer.parseInt(split[1]);
			isVertex[j] = Integer.parseInt(split[2])==0; //0表示顶点
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

        ArrayList<Object[]> resList = new ArrayList<Object[]>();

        
		for(int i = 0; i < u.length; ++i) {
            Object[] ret = new Object[4];
			ret[0] = u[i];
			ret[1] = v[i];
			ret[2] = isVertex[i];
            ret[3] = vertexFea[i];  
            resList.add(ret);
        }
		return resList;
	}

    private void secureShare(){
    }

    @Override
	public Object secureCompute() throws Exception {
        int processors = machine.getTotalMachines();
        String[] inputLengthes =machine.getInputLength().split(",");
        for(int edgeType=1;edgeType<machine.getNumOfedgeType()+1;edgeType++){
            int inputLength = Integer.parseInt(inputLengthes[edgeType-1])/ processors;
            log("inputLength:"+inputLength);
            // Map<Integer,List<Object[]>> inputDict = getInput(inputLength, machine.getGarblerId(),
            //                                                 machine.getTotalMachines(),null); //Gen Alice生成器方数据
            List<Object[]> input = getInput(inputLength, machine.getGarblerId(), machine.getTotalMachines(),edgeType); //Gen Alice生成器方数据

            String experiments = machine.getExperiments();
            List<Integer> uList=new ArrayList<>();
            List<Integer> vList=new ArrayList<>();
            List<Boolean> isVertexList=new ArrayList<>();
            int numOfNode = input.size();
            for(int j=0;j<numOfNode;j++){
                uList.add((int) input.get(j)[0]);
                vList.add((int) input.get(j)[1]);
                isVertexList.add((Boolean)input.get(j)[2]);
            } 

            //发送本方样本数量
            int tempNumOfNode = uList.size();
            env.channel.writeInt(tempNumOfNode);
            env.channel.flush();
            int otherNumOfNode = env.channel.readInt();
            log("tempNumOfNode: "+tempNumOfNode+"-----otherNumOfNode: "+otherNumOfNode);
            //如果数据不满足2的指数幂，要扩充(0,0),并且表示为边即结果不保存
            if((!isPowerOfTwo(tempNumOfNode+otherNumOfNode))){
                log("covert input lenth to power of two");
                int binaryLen =Integer.toBinaryString(tempNumOfNode+otherNumOfNode).length()-1;
                if(Party.Alice.equals(env.party)){
                    numOfNode = (2<<binaryLen)-otherNumOfNode;
                    for(int j=0;j<numOfNode-tempNumOfNode;j++){
                        //补充0 0点
                        uList.add(0);
                        vList.add(0);
                        isVertexList.add(true);
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
            log("NumOfNode: "+numOfNode+"-----otherNumOfNode: "+otherNumOfNode);

            boolean[][] uSelf = new boolean[numOfNode][GraphNode.VERTEX_LEN];
            boolean[][] vSelf= new boolean[numOfNode][GraphNode.VERTEX_LEN];
            boolean[] isVSelf = new boolean[numOfNode];
            for(int j=0;j<numOfNode;j++){
                log(uList.get(j)+" "+vList.get(j)+" ");
                uSelf[j] = Utils.fromInt(uList.get(j), GraphNode.VERTEX_LEN) ;
                vSelf[j] = Utils.fromInt(vList.get(j), GraphNode.VERTEX_LEN) ;
                isVSelf[j] = isVertexList.get(j);
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
            for (String experiment:experiments.split(",")){
                Class c = Class.forName("examples." + experiment+"Node");
                GraphNode<T>[] nodes=null;
                HashMap<Integer,Double>  resDict=null;
                switch(experiment){
                    case "histogram.Histogram":
                    nodes = (HistogramNode<T>[]) Array.newInstance(c, numOfNode+otherNumOfNode);
                    break;
                    case "pr.PageRank":
                    nodes = (PageRankNode<T>[]) Array.newInstance(c, numOfNode+otherNumOfNode);
                    break;
                }
                int AliceNodesNum = tu_a.length;
                for (int k = 0; k < nodes.length; k++) {
                    if(k<AliceNodesNum){
                        nodes[k] = new PageRankNode<T> (tu_a[k], tv_a[k], isV_a[k],env);
                    }else{
                        nodes[k] = new PageRankNode<T> (tu_b[k-AliceNodesNum], tv_b[k-AliceNodesNum], isV_b[k-AliceNodesNum],env);
                    }
                    printNode(nodes, k,edgeType);
                }
                switch(experiment){
                    case "histogram.Histogram":
                    // HashMap<Integer,Double> resDict = (new PageRank(env,machine)).secureCompute((HistogramNode<T>[]) nodes);
                    break;
                    case "pr.PageRank":
                    resDict = (new PageRank(env,machine)).secureCompute((PageRankNode<T>[]) nodes);
                }
                if(env.party.equals(Party.Alice)){
                    String fileName = experiment.split("\\.")[1]+machine.getGarblerId();
                    writeResult(fileName, machine.getJobid(),edgeType,resDict);
                }
            }
        }
        return null;
    }

}