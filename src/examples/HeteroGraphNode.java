package examples;

import parallel.GraphNode;
import util.Utils;
import flexsc.CompEnv;

public class HeteroGraphNode<T> extends GraphNode<T> {
	T[] EdgeType;
	static int OFFSET = 20;
	static int WIDTH = 40;
	static int EDGETYPE_LEN=4;

	public HeteroGraphNode(T[] u, T[] v, T isVertex, T[] EdgeType, CompEnv<T> env) {
		super(u, v, isVertex);
		this.EdgeType = EdgeType;
	}

	public HeteroGraphNode(CompEnv<T> env) {
		super(env);
		this.EdgeType = env.inputOfAlice(Utils.fromInt(0, HeteroGraphNode.EDGETYPE_LEN));
	}

	@Override
	public T[] flatten(CompEnv<T> env) {
		T[] vert = env.newTArray(1);
		vert[0] = (T) isVertex;
		return Utils.flatten(env, u, v,EdgeType, vert);
	}

	@Override
	public void unflatten(T[] flat, CompEnv<T> env) {
		T[] vert = env.newTArray(1);
		Utils.unflatten(flat, u, v, EdgeType, vert);
		isVertex = vert[0];
	}

}
