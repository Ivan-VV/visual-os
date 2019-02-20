import java.util.Queue;
import java.util.Stack;

public class Os {
    private Computer computer;//操作系统管理的裸机

    public boolean memory_table[]=new boolean[64];
    //内存页框表，下标为页框号，true表示该页框被占用，false表示该页框空闲

    public byte page_table[]=new byte[128];//页表,下标为页面号，每个byte有8位
    // 第0位为分配标志，0表示该页面空闲，1表示该页面已分配给进程
    // 第1位为驻留标志位，0表示该页面不在内存，1表示该页面已经装入内存
    // 第2-7位表示该页面装入的内存块号

    public boolean inter_flag;//中断标志位，true为中断，false不能中断

    Queue<Process>q1;//运行队列（数量只能为1或0）
    Queue<Process>q2;//就绪队列
    Queue<Process>q3;//阻塞队列
    Queue<Process>q4;//已完成的进程

    Os(Computer computer){//构造函数
        this.computer=computer;
        inter_flag=false;
        for(int i=0;i<64;i++)
            memory_table[i]=false;
        for(int i=0;i<128;i++)
            page_table[i]=0;
    }

    public void osstart()throws InterruptedException{//启动操作系统程序
        for(;;){
            synchronized (computer.clock){
                while(!inter_flag){//线程同步，确保每次时钟中断都进行了调度
                    wait();
                }
                cpu_manage();
                inter_flag=false;
                notifyAll();
            }
        }
    }

    public void memory_manage(Process process,int mode){//内存管理
        //mode=1，创建进程时调用，为进程分配页面，若此时有空余页框，则将页面装入内存
        //mode=2，进行进程上下文切换时调用，确保将进程所在页面装入内存
        //mode=3，撤销进程时调用，回收进程所用内存
        switch (mode){
            case 1:
                process.page_num=process.size%512==0?process.size/512:process.size/512+1;
                //求出为应进程分配的页面数量
                process.pages=new int[process.page_num];
                for(int i=0;i<process.page_num;i++){
                    for(int j=0;j<128;j++){//寻找尚未分配出去的页面
                        
                    }
                }
        }
    }

    public void cpu_manage(){//处理器管理

    }

    public Process create(Task task){//进程创建原语
        Process process=new Process();
        process.ProID=Process.num++;
        process.instrucnum=task.instrucnum;
        process.instruc_list=task.instruc_list;
        process.size=task.size;
        process.data_size=task.data_size;
        process.stack=new Stack();

        memory_manage(process,1);//为进程分配页面

        process.ProState=2;//创建好的进程为就绪态
        process.PSW=0;//从进程的第1条指令开始执行
        process.intime=computer.clock.gettime();
        q2.offer(process);//创建好的进程进入就绪队列
        return process;
    }

    public void destroy(Process process){//进程撤销原语
        q4.offer(process);//将该进程放入已完成队列
    }

    public void block(Process process){//进程阻塞原语
        q3.offer(process);//将该进程放入阻塞队列
    }

    public void wake(Process process){//进程唤醒原语

    }
}
