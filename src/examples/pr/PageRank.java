package examples.pr;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.HashMap;

import parallel.Gadget;
import parallel.GatherFromEdges;
import parallel.GraphNode;
import parallel.Machine;
import parallel.ScatterToEdges;
import parallel.SortGadget;
import util.Constants;
import util.Utils;
import circuits.arithmetic.ArithmeticLib;
import circuits.arithmetic.FixedPointLib;
import circuits.arithmetic.IntegerLib;
import flexsc.CompEnv;
import flexsc.Party;
import gc.BadLabelException;
import gc.GCSignal;

public class PageRank<T> extends Gadget<T> {
	static int ITERATIONS = 1;

	public PageRank(CompEnv<T> env, Machine machine) {
		super(env, machine);
	}

	private Object[] getInput(int inputLength, int garblerId, int processors) throws IOException {
		int[] u = new int[inputLength];
		int[] v = new int[inputLength];
		boolean[] isVertex = new boolean[inputLength];
		BufferedReader br = new BufferedReader(new FileReader(Constants.INPUT_DIR + "PageRank" + inputLength * processors + ".in"));
		int j = 0;
		for (int i = 0; i < inputLength * processors; i++) {
			String readLine = br.readLine();
			if (!(i >= garblerId * inputLength && i < (garblerId + 1) * inputLength)) {
				continue;
			}
			String[] split = readLine.split(" ");
			u[j] = Integer.parseInt(split[0]);
			v[j] = Integer.parseInt(split[1]);
			isVertex[j] = (Integer.parseInt(split[2]) == 1);
			j++;
		}
		br.close();
		boolean[][] a = new boolean[u.length][];
		boolean[][] b = new boolean[v.length][];
		boolean[] c = new boolean[isVertex.length];
		for(int i = 0; i < u.length; ++i) {
			a[i] = Utils.fromInt(u[i], GraphNode.VERTEX_LEN);
			b[i] = Utils.fromInt(v[i], GraphNode.VERTEX_LEN);
			c[i] = isVertex[i];
		}
		Object[] ret = new Object[3];
		ret[0] = a;
		ret[1] = b;
		ret[2] = c;
		return ret;
	}

    private HashMap<Integer,Double> getResult(int garblerId,
			final CompEnv<T> env,
			PageRankNode<T>[] pr,
			int iterations,
			ArithmeticLib<T> flib) throws IOException, BadLabelException {
        HashMap<Integer,Double> resDict = new HashMap<Integer,Double>();
		if (garblerId == 0 && Party.Alice.equals(env.getParty())) {
			System.out.println("PageRank of vertices after " + iterations + " iteration(s):");
		}
		for (int i = 0; i < pr.length; i++) {
			int u = Utils.toInt(env.outputToAlice(pr[i].u));
			double pageRank = flib.outputToAlice(pr[i].pr);
			boolean e = env.outputToAlice(pr[i].isVertex);
			env.channel.flush();
			if (Party.Alice.equals(env.party)) {
				if (e&&u!=0) {
                    resDict.put(u,pageRank);
					System.out.format("%d %.2f\n", u, pageRank);
				}
			}
	    }
        return resDict;
	}

	@Override
	public Object secureCompute() throws Exception {
		String[] inputLengthes =machine.getInputLength().split(",");
        int inputLength = Integer.parseInt(inputLengthes[0])/ machine.getTotalMachines();
        env.channel.writeInt(inputLength);
        env.channel.flush();
		int otherInputLength = env.channel.readInt();
		boolean[][] u = null;
		boolean[][] v = null;
		boolean[] isV = null;
		boolean[][] u_a = null;
		boolean[][] v_a = null;
		PageRankNode<T>[] nodes_a=null;
		boolean[] isV_a = null;
		boolean[][] u_b = null;
		boolean[][] v_b = null;
		boolean[] isV_b = null;
		PageRankNode<T>[] nodes_b=null;
		PageRankNode<T>[] nodes=null;
		if (env.getParty().equals(Party.Alice)) { 
			if (inputLength>0){ 
				System.out.println("Alice inputLength "+(inputLength));
				Object[] input = getInput(inputLength, machine.getGarblerId(), machine.getTotalMachines()); //Gen Alice生成器方数据
				u_a = (boolean[][]) input[0];
				v_a = (boolean[][]) input[1];
				isV_a = (boolean[]) input[2];
			}
			System.out.println("Bob inputLength "+(otherInputLength));
			u_b = new boolean[otherInputLength][GraphNode.VERTEX_LEN];
			v_b = new boolean[otherInputLength][GraphNode.VERTEX_LEN];
			isV_b = new boolean[otherInputLength];
		} else { 
			if (otherInputLength>0){
				System.out.println("Alice inputLength"+(otherInputLength));
				u_a = new boolean[otherInputLength][GraphNode.VERTEX_LEN];
				v_a = new boolean[otherInputLength][GraphNode.VERTEX_LEN];
				isV_a = new boolean[otherInputLength];
			}
			System.out.println("Bob inputLength "+(inputLength));
			Object[] input = getInput(inputLength, machine.getGarblerId(), machine.getTotalMachines()); //EVa Bob执行器侧数据
			u_b = (boolean[][]) input[0];
			v_b = (boolean[][]) input[1];
			isV_b = (boolean[]) input[2];
		}
		if (u_a!=null){
			T[][] tu_a = (T[][]) env.inputOfAlice(u_a);
			T[][] tv_a = (T[][]) env.inputOfAlice(v_a);
			T[] tIsV_a = (T[]) env.inputOfAlice(isV_a);
			nodes_a = (PageRankNode<T>[]) Array.newInstance(PageRankNode.class, u_a.length);
			for (int i = 0; i < nodes_a.length; i++) {
				nodes_a[i] = new PageRankNode<T>(tu_a[i], tv_a[i], tIsV_a[i], env);
			}
		}
		T[][] tu_b = (T[][]) env.inputOfBob(u_b);
		T[][] tv_b = (T[][]) env.inputOfBob(v_b);
		T[] tIsV_b = (T[]) env.inputOfBob(isV_b);
		// 通过OT 获取的顶点数据，秘密分享在哪里呢？
		System.out.println("bbbbbbbbbbbbbbb "+(((GCSignal[])tu_b[0])[0].toHexStr()));
		nodes_b = (PageRankNode<T>[]) Array.newInstance(PageRankNode.class, u_b.length);
		for (int i = 0; i < nodes_b.length; i++) {
			nodes_b[i] = new PageRankNode<T>(tu_b[i], tv_b[i], tIsV_b[i], env);
			System.out.println("hhhhhhhhhhhhhhhh "+(((GCSignal) nodes_b[i].isVertex)).equals( nodes_b[0].isVertex));
		}
		if (u_a!=null){
			nodes = (PageRankNode<T>[]) Array.newInstance(PageRankNode.class, u_a.length+u_b.length);
			for (int i = 0; i < nodes.length; i++) {
				if (i<u_a.length){
					nodes[i]=nodes_a[i];
				}else{
					nodes[i] = nodes_b[i-u_a.length];
					// PageRankNode<T>[] temp=new PageRankNode[1];
					// temp[0] = nodes[i-u_a.length];
					// T in = nodes_b[i].inset(temp,nodes_b[i],env);
					// GraphNode<T> ret;
					// ret = nodes_b[i-u_a.length].mux(nodes_b[i-u_a.length],in,env);
					// nodes[i].u = ret.u;
					// nodes[i].v = ret.v;
					// nodes[i].isVertex = ret.isVertex;
				}
			}
		}
		else{
			nodes=nodes_b;
		}
		
		return secureCompute(nodes);
	}

	private <T> void print(int garblerId,
			final CompEnv<T> env,
			PageRankNode<T>[] pr,
			int iterations,
			ArithmeticLib<T> flib) throws IOException, BadLabelException {
		if (garblerId == 0 && Party.Alice.equals(env.getParty())) {
			System.out.println("PageRank of vertices after " + iterations + " iteration(s):");
		}
		for (int i = 0; i < pr.length; i++) {
			int u = Utils.toInt(env.outputToAlice(pr[i].u));
			double pageRank = flib.outputToAlice(pr[i].pr);
			boolean e = env.outputToAlice(pr[i].isVertex);
			env.channel.flush();
			if (Party.Alice.equals(env.party)) {
				if (e&&u!=0) {
					System.out.format("%d %.2f\n", u, pageRank);
				}
			}
	    }
	}

    public HashMap<Integer,Double> secureCompute(PageRankNode<T>[] nodes) throws Exception {
		// business logic
		final IntegerLib<T> lib = new IntegerLib<>(env);  //实现秘态的加减乘除、逻辑运算等
		final ArithmeticLib<T> flib = new FixedPointLib<T>(env, PageRankNode.WIDTH, PageRankNode.OFFSET);//实现定点数秘态的加减乘除、逻辑运算等
        
		// set initial pagerank
		new SetInitialPageRankGadget<T>(env, machine)
				.setInputs(nodes)
				.secureCompute();

		// 1. Compute number of neighbors for each vertex
		new GatherFromEdges<T>(env, machine, false /* isEdgeIncoming */, new PageRankNode<T>(env)) {

			@Override
			public GraphNode<T> aggFunc(GraphNode<T> aggNode, GraphNode<T> bNode) {
				PageRankNode<T> agg = (PageRankNode<T>) aggNode;
				PageRankNode<T> b = (PageRankNode<T>) bNode;
				PageRankNode<T> ret = new PageRankNode<T>(env);
				ret.l = flib.add(agg.l, b.l);
				return ret;
			}

			@Override
			public void writeToVertex(GraphNode<T> aggNode, GraphNode<T> bNode) {
				PageRankNode<T> agg = (PageRankNode<T>) aggNode;
				PageRankNode<T> b = (PageRankNode<T>) bNode;
				b.l = lib.mux(b.l, agg.l, b.isVertex);
			}
		}.setInputs(nodes).secureCompute();

		for (int i = 0; i < ITERATIONS; i++) {
			// 2. Write weighted PR to edges
			new ScatterToEdges<T>(env, machine, false /* isEdgeIncoming */) {

				@Override
				public void writeToEdge(GraphNode<T> vertexNode,
						GraphNode<T> edgeNode, T cond) {
					PageRankNode<T> vertex = (PageRankNode<T>) vertexNode;
					PageRankNode<T> edge = (PageRankNode<T>) edgeNode;
					T[] div = flib.div(vertex.pr, vertex.l);
					edge.pr = lib.mux(div, edge.pr, cond);
				}
			}.setInputs(nodes).secureCompute();

			// 3. Compute PR based on edges
			new GatherFromEdges<T>(env, machine, true /* isEdgeIncoming */, new PageRankNode<T>(env)) {

				@Override
				public GraphNode<T> aggFunc(GraphNode<T> aggNode, GraphNode<T> bNode) {
					PageRankNode<T> agg = (PageRankNode<T>) aggNode;
					PageRankNode<T> b = (PageRankNode<T>) bNode;

					PageRankNode<T> ret = new PageRankNode<T>(env);
					ret.pr = flib.add(agg.pr, b.pr);
					return ret;
				}

				@Override
				public void writeToVertex(GraphNode<T> aggNode, GraphNode<T> bNode) {
					PageRankNode<T> agg = (PageRankNode<T>) aggNode;
					PageRankNode<T> b = (PageRankNode<T>) bNode;
					b.pr = lib.mux(b.pr, agg.pr, b.isVertex);
				}
			}.setInputs(nodes).secureCompute();
		}
		new SortGadget<T>(env, machine)
			.setInputs(nodes, PageRankNode.allVerticesFirst(env))
			.secureCompute();
		// print(machine.getGarblerId(), env, nodes, ITERATIONS, flib);
		return getResult(machine.getGarblerId(), env, nodes, ITERATIONS, flib);
	}
}