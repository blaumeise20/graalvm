arch -arch x86_64 zsh
export MX_PYTHON=/usr/local/pypy3.10-x86_64/bin/pypy
function my {
    if [[ "$1" == "-d" ]]; then
        shift
        command=$1
        shift
        mx --env jvm-ce-llvm --native-images= $command --vm.agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=8000 $@
    else
        mx --env jvm-ce-llvm --native-images= $@
    fi
}
# --experimental-options --llvm.llDebug=true --log.llvm.TraceIR.level=FINER
my espresso --experimental-options --vm.Dgraal.Dump=:1 --vm.Dgraal.PrintGraph=Network --engine.BackgroundCompilation=false --engine.TraceCompilation -cp ../../java_hello_world _________IsPrime_________ 19259 123
my espresso --experimental-options --vm.Dgraal.Dump=:1 --vm.Dgraal.PrintGraph=Network --engine.CompileImmediately -cp ../../java_hello_world _________IsPrime_________ 19259 50
my espresso --experimental-options --vm.Dgraal.Dump=:1 --vm.Dgraal.PrintGraph=Network --engine.CompileImmediately -cp <INSERT_CLASS_PATH> _________IsPrime_________ 19259 50