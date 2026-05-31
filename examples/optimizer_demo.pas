program OptimizerDemo;

var
  total: integer;

begin
  total := 0;

  if false then
    total := 100
  else
    total := total + 2;

  while false do
    total := total + 1000;

  total := total + (3 * (4 + 1));
  total := total - 0;

  if not not true then
    total := total + 0;

  writeln('OPT DEMO ', total)
end.
