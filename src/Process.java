import java.util.Vector;

public class Process {//进程类
    static int num;//上一条生成PCB的序号
    int ProID;//进程序号
    int ProState;//1为运行态，2为就绪态，3为等待态
    int InstrucNum;//进程的指令数目
    Vector instruc_list=new Vector();//进程包含指令的容器
    int PSW;//当前指令编号

    public static void create(Job job){//进程创建原语

    }

    public static void destroy(){//进程撤销原语

    }

    public static void block(){//进程阻塞原语

    }

    public static void wake(){//进程唤醒原语

    }
}
