set datafile commentschars '#'
set title 'Add Search Throughput Variation'
set style data histogram
set bars fullwidth
set style histogram errorbars gap 1
set style fill solid border -1
set offsets graph 0, 0, 0.05, 0.05
set key autotitle columnhead
set key outside
set xlabel 'Number of threads'
set xtics rotate by -45
set ylabel 'Operations/second'
set yrange [0<*:]
set term png
set output 'output2/add_search.png'
plot for [DS=2:6:2] 'output2/add_search.dat' using DS:DS+1:xticlabels(1)