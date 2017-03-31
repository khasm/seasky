set datafile commentschars '#'
set title 'Add Search Throughput Variation'
set style data histogram
set style histogram cluster gap 1
set style fill solid border -1
set offsets graph 0, 0, 0.05, 0.05
set key autotitle columnhead
set key outside
set xlabel 'Number of threads'
set ylabel 'Operations/second'
set term png
set output '/output/add_search.png'
plot for [DS=2:4:1] '/output/add_search.dat' using DS:xticlabels(1)