set datafile commentschars '#'
set title 'Mime vs Unstructured Precision'
set style data histogram
set bars fullwidth
set style histogram errorbars gap 1
set style fill solid border -1
set offsets graph 0, 0, 0.05, 0.05
set key off
set xlabel 'Execution profile'
set xtics rotate by -45
set ylabel 'Search precision'
set yrange [0<*:]
set term png
set output 'output2/precision_1.png'
plot for [DS=2:2:2] 'output2/precision_1.dat' using DS:DS+1:xticlabels(1)