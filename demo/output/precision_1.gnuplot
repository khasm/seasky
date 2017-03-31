set datafile commentschars '#'
set title 'Mime vs Unstructured Precision'
set style data histogram
set style histogram cluster gap 1
set style fill solid border -1
set offsets graph 0, 0, 0.05, 0.05
set key off
set xlabel 'Execution profile'
set ylabel 'Search precision'
set term png
set output '/output/precision_1.png'
plot for [DS=2:2:1] '/output/precision_1.dat' using DS:xticlabels(1)