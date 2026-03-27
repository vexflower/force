package model;


public class Model
{
    public static Model cube;
    public static Model square;

    static {
        float[] squareVertices = { -1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f };
        // You must actually load it into the GPU!
        //square = ModelLoader.loadToVao(squareVertices, 2);
    }


    private int vaoId;
    private int vertexCount;


    public Model(int vaoId, int length)
    {
        this.vaoId = vaoId;
        this.vertexCount = length;
    }

    public int getVaoId()
    {
        return vaoId;
    }

    public void setVaoId(int vaoId)
    {
        this.vaoId = vaoId;
    }

    public int getVertexCount()
    {
        return vertexCount;
    }

    public void setVertexCount(int vertexCount)
    {
        this.vertexCount = vertexCount;
    }
}
