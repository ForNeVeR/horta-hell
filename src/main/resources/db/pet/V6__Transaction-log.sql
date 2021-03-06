create table PetTransaction (
  id integer auto_increment primary key,
  room varchar(255),
  nickname varchar(255),
  time timestamp,
  change integer,
  reason varchar(255)
);

create index PetTransaction_room_nickname on PetTransaction(room, nickname);
create index PetTransaction_time on PetTransaction(time);