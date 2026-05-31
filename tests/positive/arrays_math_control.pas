program ArraysMathControl;

var
  a: array[1..4] of integer;
  i, sum: integer;
  d: double;
  ok: boolean;

function twice(x: integer): integer;
begin
  twice := x * 2
end;

begin
  sum := 0;
  for i := 1 to 4 do
  begin
    a[i] := twice(i);
    sum := sum + a[i]
  end;

  d := abs(-3) + 0.5;
  ok := (sum = 20) and (d > 3.0);

  if ok then
    writeln('OK arrays math control ', sum, ' ', d)
  else
    writeln('FAIL')
end.
