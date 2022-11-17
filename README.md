# JavaCallGraph
基于[Soot](https://github.com/soot-oss/soot)的调用图构建

## Prerequisites
JDK目前需要用JDK8，JDK9之后引入了Module，JDK代码结果发生很大变化，Soot程序分析会有问题，官方已经有解决方案，但是还未尝试

## How to run

通过JSON配置文件指定运行参数，包括
- 项目名称
- 本地路径：war包、jar包、class文件的路径，多个以分号分隔，如果是目录，将自动遍历目录找到所有的war包、jar包和class文件
- 分析入口：分析的入口方法，PackageInclusion（包含指定包名的类）、PackageExclusion（过滤指定包名的类）、MethodInclusion（包含指定方法）、MethodExclusion（过滤指定方法），按照上述顺序依次执行过滤，可以仅指定部分包名或方法名，多个以分号分割
- 构建类型：函数调用图的构建方法，包括RTA、Spark、Geom，按顺序精确度越来越高，但是运行开销（时间和空间）也越来越大，默认RTA

例如：
```json
{
  "Name": "test",
  "Path": "",
  "EntranceSetting": {
    "PackageInclusion" : "",
    "PackageExclusion" : "",
    "MethodInclusion" : "",
    "MethodExclusion" : ""
  },
  "CGType" : "CHA"
}
```