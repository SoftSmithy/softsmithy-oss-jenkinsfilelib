# SoftSmithy OSS Jenkinsfile shared library

An shared libriary which can be used in *Jenkinsfiles*.

## Usage in Jenkinsfile:

```groovy
@Library('softsmithy-oss-jenkinsfilelib') _

javalib(ProjectType.JAVA_LIB, 'Apache Maven 3.6', 'Java SE 11')
```

**Note**: The exact parameter values depend on your Jenkins configuration.