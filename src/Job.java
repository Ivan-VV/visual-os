public class Job {//作业类
    static int time=0;//上一个作业的提交时间
    public int intime;//提交作业的时间
    public int task_num;//作业包含的任务数目
    public Task task_list[];//作业包含的任务集

    Job(){
        intime=time+((1+(int)(Math.random()*100))*10);//每一个作业比上一个作业晚提交10ms-1000ms，为10的倍数
        time=intime;
        task_num=1+(int)(Math.random()*5);//一个作业包含1-5个任务
        task_list=new Task[task_num];
        for(int i=0;i<task_num;i++){
            Task task=new Task();
            task_list[i]=task;
        }
    }
}

class Task{//任务类，一个作业可以分解为多个任务，每个任务创建一个进程
    public int size;//任务所需内存大小,单位是内存单元个数
    public int instrucnum;//任务包含的指令数目
    public Instruct instruc_list[];//任务包含的指令序列

    Task(){
        size=512+(int)(Math.random()*32257);//任务所需内存大小512B-32768B
        instrucnum=20+(int)(Math.random()*281);//任务包含20-300条指令
        instruc_list=new Instruct[instrucnum];
        for(int i=0;i<instrucnum;i++){
            Instruct instruct=new Instruct();
            instruct.Instruc_ID=i;//指令序号
            instruct.Instruc_State=(int)(Math.random()*3);//每条指令类型为0或1或2，0表示系统调用，1表示用户态计算操作，2表示PV操作
            instruct.Instruct_Times=(10+(int)(Math.random()*41))*10;//每条指令运行时间为100ms-500ms，为10的倍数
            instruc_list[i]=instruct;
        }
    }
}
