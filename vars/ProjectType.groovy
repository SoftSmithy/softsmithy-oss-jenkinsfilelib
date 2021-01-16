enum ProjectType {
    LIBRARY(true),
    APPLICATION(true),
    POM(false);
//        DROMBLER_FX(true),
//        SPRING_BOOT(true),
//        GLUON_MOBILE(true);

    boolean containingJavaSourceFiles;

    ProjectType(boolean containingJavaSourceFiles) {
        this.containingJavaSourceFiles = containingJavaSourceFiles;
    }
    }