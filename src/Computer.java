public class Computer {//裸机类
    short page_table;//页表基址寄存器，存放页表基址
    public Clock clock;//时钟
    private CPU cpu;//CPU
    private BUS bus;//总线
    public MMU mmu;//存储管理部件
    private Memory memory;//内存
    private Disk disk;//硬盘

    Computer(){//构造函数
        page_table=0;
    }

    public void computerstart(){//开机
        Os os=new Os(this);//初始化操作系统
        new Thread(){//启动时钟
            public void run(){
                try {
                    clock.clockstart(os);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }.start();
        new Thread(){//操作系统等待时钟中断进行调度
            public void run(){
                try {
                    os.osstart();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }
}

class Clock{//时钟类
    private int time;//记录系统时间，以毫秒为单位

    Clock(){
        time=0;//系统时间初始为0
    }

    public void clockstart(Os os)throws InterruptedException{//每隔10毫秒系统时间加10并进行时钟中断
        for(;;) {
            Thread.sleep(10);
            synchronized(this){//给Clock对象加锁
                while(os.inter_flag){//线程同步，确保每次时钟中断都进行了调度
                    wait();
                }
                time += 10;
            }
            interrupt(os);
            if(os.end_flag) break;
        }
    }

    public synchronized void interrupt(Os os) throws InterruptedException{//时钟中断
        os.inter_flag=true;
        notifyAll();
    }

    public synchronized int gettime(){//获取当前时间
        return time;
    }
}

class CPU{//CPU类
    private short PC;//程序计数器
    private short IR;//指令寄存器
    private int []PSW=new int [2];//指令状态字寄存器
    private int cpu_state;//CPU状态，表示内核态，1表示用户态

}

class BUS{//总线类
    private short addbus;//16位地址线
    private short databus;//16位数据线
}

class MMU{//存储管理部件类
    public short add_change(Os os,short vir_add){//将逻辑地址转换为物理地址
        short real_add=0;
        return real_add;
    }
}

class Memory{//内存类
    public int memory[]=new int[64];
    //共32KB,每个物理块大小512B,共64个物理块,-1表示该物理块空闲，非负表示该物理块被相应序号进程占用

    Memory(){//内存初始化
        for(int i=0;i<64;i++)
            memory[i]=-1;
    }

}

class Disk{//硬盘类
    public boolean disk[][]=new boolean[32][64];
    //1 个磁道中有64个扇区，1个柱面中有32个磁道,1个扇区为1个物理块，每个物理块大小512B，合计1MB,true表示该物理块被占用，false表示该物理块空闲
}
