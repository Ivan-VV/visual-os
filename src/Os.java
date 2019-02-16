public class Os {
    private Computer computer;//操作系统管理的裸机

    Os(Computer computer){//构造函数
        this.computer=computer;
    }

    public void osstart(){//启动操作系统程序
            new Thread(){//启动内存管理
                public void run(){
                    memory_manage();
                }
            }.start();
            new Thread(){//启动处理器管理
                public void run(){
                    cpu_manage();
                }
            }.start();
    }

    public void memory_manage(){//内存管理

    }

    public void cpu_manage(){//处理器管理

    }
}
