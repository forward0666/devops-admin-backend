with open("pom.xml", "r") as f:
    content = f.read()

old = '            <!-- Jackson BOM - 强制统一版本 -->\n            <dependency>\n                <groupId>com.fasterxml.jackson</groupId>\n                <artifactId>jackson-bom</artifactId>\n                <version>${jackson.version}</version>\n                <type>pom</type>\n                <scope>import</scope>\n            </dependency>\n            <!-- Spring Cloud 依赖管理 -->\n            <dependency>\n                <groupId>org.springframework.cloud</groupId>\n                <artifactId>spring-cloud-dependencies</artifactId>\n                <version>${spring-cloud.version}</version>\n                <type>pom</type>\n                <scope>import</scope>\n            </dependency>\n\n            <!-- Spring Cloud Alibaba 依赖管理 -->\n            <dependency>\n                <groupId>com.alibaba.cloud</groupId>\n                <artifactId>spring-cloud-alibaba-dependencies</artifactId>\n                <version>${spring-cloud-alibaba.version}</version>\n                <type>pom</type>\n                <scope>import</scope>\n            </dependency>'

new = '            <!-- Spring Cloud 依赖管理 -->\n            <dependency>\n                <groupId>org.springframework.cloud</groupId>\n                <artifactId>spring-cloud-dependencies</artifactId>\n                <version>${spring-cloud.version}</version>\n                <type>pom</type>\n                <scope>import</scope>\n            </dependency>\n\n            <!-- Spring Cloud Alibaba 依赖管理 -->\n            <dependency>\n                <groupId>com.alibaba.cloud</groupId>\n                <artifactId>spring-cloud-alibaba-dependencies</artifactId>\n                <version>${spring-cloud-alibaba.version}</version>\n                <type>pom</type>\n                <scope>import</scope>\n            </dependency>\n\n            <!-- Jackson BOM - 必须在 Alibaba BOM 之后 -->\n            <dependency>\n                <groupId>com.fasterxml.jackson</groupId>\n                <artifactId>jackson-bom</artifactId>\n                <version>${jackson.version}</version>\n                <type>pom</type>\n                <scope>import</scope>\n            </dependency>'

if old in content:
    content = content.replace(old, new, 1)
    with open("pom.xml", "w") as f:
        f.write(content)
    print("OK")
else:
    print("NOT FOUND")