set datafile commentschars '#'
set title 'Depsky and Ramcloud Comparision'
set style data histogram
set style histogram cluster gap 1
set style fill solid border -1
set offsets graph 0, 0, 0.05, 0.05
set key autotitle columnhead
set key outside
set xlabel ''
set xtics rotate by -45
set ylabel 'Total Time (second)'
set term png
set output 'output/depsky2.png'
plot for [DS=2:2:1] 'output/depsky2.dat' using DS:xticlabels(1), \
	 for [DS=2:2:1] 'output/ramcloud2.dat' using DS:xticlabels(1)