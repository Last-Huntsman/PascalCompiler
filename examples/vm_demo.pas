program VmDemo;

const
  Limit = 5;

var
  values: array[1..5] of integer;
  i, sum: integer;
  text: string;

function square(x: integer): integer;
begin
  square := x * x
end;

begin
  sum := 0;

  for i := 1 to Limit do
  begin
    values[i] := square(i);
    if i = 3 then
      continue;
    sum := sum + values[i]
  end;

  text := copy('vm-demo', 1, 2) + '-' + copy('runtime', 1, 2);

  writeln('VM SUM ', sum);
  writeln('VM TEXT ', text);
  writeln('VM POS ', pos('de', 'vm-demo'))
end.
