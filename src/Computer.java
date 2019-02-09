public class Computer {//裸机类
    short page_table;//页表基址寄存器，存放页表基址
    private Clock clock;//时钟
    private CPU cpu;//CPU
    private BUS bus;//总线
    private MMU mmu;//存储管理部件
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
                    clock.clockstart();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }.start();
        new Thread(){
            public void run(){
                os.osstart();
            }
        }.start();//开始执行操作系统程序
    }

    public int gettime(){//获取当前时间
        return clock.gettime();
    }
}

class Clock{//时钟类
    private int time;//记录系统时间，以毫秒为单位

    Clock(){
        time=0;//系统时间初始为0
    }

    public void clockstart()throws InterruptedException{//每隔10毫秒系统时间加10
        for(;;) {
            Thread.sleep(10);
            time += 10;
        }
    }

    public int gettime(){
        return time;
    }//获取当前时间
}

class CPU{//CPU类
    private short PC;//程序计数器
    private short IR;//指令寄存器
    private int []PSW=new int [2];//指令状态字寄存器

}

class BUS{//总线类
    private short addbus;//16位地址线
    private short databus;//16位数据线
}

class MMU{//存储管理部件类

}

class Memory{//内存类
    private boolean memory[][]=new boolean[64][512];
    //共32KB,每个物理块大小512B,共64个物理块,true表示该存储单元被占用，false表示该存储单元空闲

    Memory(){//内存初始化
        for(int i=0;i<64;i++)
            for(int j=0;j<512;j++)
                memory[i][j]=false;
    }

}

class Disk{//硬盘类

}
