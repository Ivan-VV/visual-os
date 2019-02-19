import java.util.Queue;

public class Os {
    private Computer computer;//操作系统管理的裸机
    public boolean inter_flag;//中断标志位，true为中断，false不能中断
    Queue<Process>q1;//运行队列（数量只能为1或0）
    Queue<Process>q2;//就绪队列
    Queue<Process>q3;//阻塞队列
    Queue<Process>q4;//已完成的进程

    Os(Computer computer){//构造函数
        this.computer=computer;
        inter_flag=false;
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

    public void memory_manage(){//内存管理

    }

    public void cpu_manage(){//处理器管理

    }
}
