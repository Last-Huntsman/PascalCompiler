program VmLoopControl;

var
  i, sum: integer;

begin
  sum := 0;
  i := 0;

  repeat
    inc(i);
    if i = 2 then
      continue;
    if i = 5 then
      break;
    sum := sum + i
  until i > 10;

  writeln('VM loop ', sum)
end.
