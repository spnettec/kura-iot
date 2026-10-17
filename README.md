# kura-iot

基于 Eclipse Kura 和 Vert.x 的物联网边缘网关扩展项目，采用 Java、OSGi 和 Maven 多模块构建，提供公共组件及设备接入、数据处理相关应用模块。

项目仓库：[spnettec/kura-iot](https://github.com/spnettec/kura-iot)。

## 构建准备

- 使用 JDK 21，并将 `JAVA_HOME` 指向该 JDK。
- 安装 Maven。
- 将配套 Kura 构建支持、API、运行时库及匹配的 PLC4X 依赖安装到同一个 Maven 本地缓存。具体版本以各模块的 `pom.xml` 为准。
- 根项目依赖 `org.eclipse.kura:kura-build-parent:6.0.0-SNAPSHOT`；构建前需准备好该父 POM 及其依赖。

## 获取与构建

```bash
git clone https://github.com/spnettec/kura-iot.git
cd kura-iot
mvn -Dmaven.repo.local="$HOME/.m2/repository" clean install -DskipTests --fail-at-end
```

构建产物位于各模块的 `target/` 目录，包括 OSGi bundle；配置了部署包插件的模块还会生成 `.dp` 部署包。

在依赖和对应测试环境准备好后，可运行：

```bash
mvn -Dmaven.repo.local="$HOME/.m2/repository" test -DskipTests=false --fail-at-end
```

现有源码包名、模块目录和 Maven 坐标保持不变；构建单个模块时，请使用该模块在源码中的实际路径。

## 参与贡献

1. Fork 本仓库并创建功能分支。
2. 提交范围明确的修改，完成相关验证。
3. 向 [本仓库](https://github.com/spnettec/kura-iot) 发起 Pull Request，说明修改目的和验证结果。

请保留源码及第三方组件中已有的版权和许可证声明。
