import java.util.Vector;

public class Job {//作业类
    public int size;//作业所需内存大小,单位是内存单元个数
    public int type;//作业类型
    public int InstrucNum;//作业的指令数目
    Vector instruc_list=new Vector();//作业包含指令的容器

    Job(){
        int i;
        for(i=0;i<InstrucNum;i++){
            Instruct instruct=new Instruct();
            instruc_list.add(instruct);
        }
    }
}
