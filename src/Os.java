import java.util.Queue;

public class Os {
    private Computer computer;//操作系统管理的裸机
    Queue<Process>q1;//运行队列（数量只能为1或0）
    Queue<Process>q2;//就绪队列
    Queue<Process>q3;//等待队列
    Queue<Process>q4;//已完成的进程

    Os(Computer computer){//构造函数
        this.computer=computer;
    }

    public void osstart()throws InterruptedException{//启动操作系统程序
        for(;;){
            synchronized (computer.clock){
                wait();
                cpu_manage();
                notifyAll();
            }
        }
    }

    public void memory_manage(){//内存管理

    }

    public void cpu_manage(){//处理器管理

    }
}
