set datafile commentschars '#'
set title 'Depsky and Ramcloud Client Times Comparision'
set style data histogram
set bars fullwidth
set style histogram errorbars gap 1
set style fill solid border -1
set offsets graph 0, 0, 0.05, 0.05
set key autotitle columnhead
set key outside
set xlabel ''
set xtics rotate by -45
set ylabel 'Total Time (second)'
set yrange [0<*:]
set term png
set output 'output2/depsky_client2.png'
plot for [DS=2:2:2] 'output2/depsky_client2.dat' using DS:DS+1:xticlabels(1), \
	 for [DS=2:2:2] 'output2/ramcloud_client2.dat' using DS:DS+1:xticlabels(1)