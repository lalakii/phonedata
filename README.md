# 手机号码归属地信息库、手机号归属地查询

[![Maven Central](https://img.shields.io/maven-central/v/cn.lalaki/phone_location.svg?label=Maven%20Central&logo=sonatype)](https://central.sonatype.com/artifact/cn.lalaki/phone_location/)
[![License: Apache-2.0 (shields.io)](https://img.shields.io/badge/GNU-GPLv3-A40000?logo=gnu)](./LICENSE)

**本项目移植自 [xluohome/phonedata](https://github.com/xluohome/phonedata)，实现了手机号归属地查询功能的
Java 版本，同时兼容 Android 设备，并已发布至 Maven
中央仓库。如果有缺失的手机号段，请下载 [phone.xlsx](./phone.xlsx)
文件添加到表格再提交到这里，不想提交PR也可以直接发到我的邮箱[i@lalaki.cn](mailto:i@lalaki.cn)。如果你需要
Xlsx 转 Dat，可以点[这里](https://github.com/lalakii/phonedata/releases)下载现成的工具。**

## 更新日志

基于原作者2302版本的数据新增,
[点此查看更新了哪些号段](https://github.com/lalakii/phonedata/releases)

## Gradle

```kts
dependencies {
    implementation("cn.lalaki:phone_location:1.2.0") // Java 引用这个

    implementation("cn.lalaki:phone_location_android:1.2.1") // Android 引用这个 (AAR包)

    // 数据库资源文件 (可选), 若不引入, 则需要通过 loadDAT 函数指定数据库文件 phone.dat
    implementation("cn.lalaki:phone_location:1.2.0:resources")
}
```

## 代码示例

```java
import cn.lalaki.phone.location.PhoneLocation;
import cn.lalaki.phone.location.PhoneLocation.PhoneInfo;

public class Example {
    public static void main(String[] args) {
        // 查询 11位 手机号 或 手机号的 前7位
        PhoneInfo info = PhoneLocation.queryPhoneInfo("your tel number");
        System.out.println(info.province()); // 省
        System.out.println(info.city()); // 市
        System.out.println(info.zipCode()); // 邮编
        System.out.println(info.areaCode()); // 区号
        System.out.println(info.carrier()); // 运营商, 受携号转网业务影响, 此处获取的运营商不准确
        System.out.println(info.version()); // 数据库版本

        // 可选, 自定义数据库缓存路径, 必须是一个支持读写的文件夹, 需要在加载数据库文件之前执行
        PhoneLocation.setTempDirectory(new File("your_temp_directory"));
        // 对于 Android 设备, 这样设置下兼容性更好
        // PhoneLocation.setTempDirectory(getCacheDir());

        // 可选, 自定义数据库文件, 完整引用时，也可以自定义数据库文件, 此选项的优先级最高
        PhoneLocation.loadDAT(new File("your_phone_dat"));

        // 可选, 清理数据库缓存
        PhoneLocation.clearCache();
    }
}
```

## LICENSE

[GPL-3.0](./LICENSE)

## By lalaki.cn
