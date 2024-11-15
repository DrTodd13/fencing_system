class single_step:
    def __init__(self, cap):
        self.cap = cap
        self.next_frame = 0
        
    def set(self, frame_number):
        while self.next_frame < frame_number:
            self.cap.read()
            self.next_frame += 1
            if self.next_frame % 10000 == 0:
                print("single_step", self.next_frame)

    def get(self):
        return self.next_frame
    
    def read(self):
        self.next_frame += 1
        return self.cap.read()